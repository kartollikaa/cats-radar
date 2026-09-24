package dev.catsradar.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupReader
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.platform.BackupWriter
import dev.catsradar.domain.platform.DeviceIdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.time.Clock

private val ArchiveJson = Json { ignoreUnknownKeys = true }

internal fun Context.openWrite(target: String): OutputStream? = when {
    target.startsWith("content://") -> contentResolver.openOutputStream(Uri.parse(target), "wt")
    else -> FileOutputStream(target)
}

internal fun Context.openRead(source: String): InputStream? = when {
    source.startsWith("content://") -> contentResolver.openInputStream(Uri.parse(source))
    else -> FileInputStream(source)
}

class ZipBackupWriter(
    private val context: Context,
    private val photoStorage: AndroidPhotoStorage,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: Clock,
    private val appVersion: String,
    private val openTarget: (String) -> OutputStream? = context::openWrite,
) : BackupWriter {

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // any I/O failure is the same answer
    override suspend fun write(target: String, contents: BackupContents): Boolean =
        withContext(Dispatchers.IO) {
            val written = try {
                writeArchive(target, contents)
            } catch (e: Exception) {
                false
            }
            // The picker creates the document before this runs; left empty or half-written, it would
            // look like a backup.
            if (!written) discard(target)
            written
        }

    private fun writeArchive(target: String, contents: BackupContents): Boolean {
        val stream = openTarget(target) ?: return false
        ZipOutputStream(stream.buffered()).use { zip ->
            zip.putJson(MANIFEST_ENTRY, ArchiveJson.encodeToString(manifest()))
            zip.putJson(ENCOUNTERS_ENTRY, ArchiveJson.encodeToString(contents.encounters.map { it.toRecord() }))
            zip.putJson(PLACE_CELLS_ENTRY, ArchiveJson.encodeToString(contents.placeCells.map { it.toRecord() }))
            zip.putJson(WALKS_ENTRY, ArchiveJson.encodeToString(contents.walks.map { it.toRecord() }))
            zip.putJson(TRACK_POINTS_ENTRY, ArchiveJson.encodeToString(contents.trackPoints.map { it.toRecord() }))
            contents.encounters.flatMap { listOfNotNull(it.photoPath, it.thumbPath) }.distinct().forEach { path ->
                readPhoto(path)?.let { zip.putEntry(PHOTOS_PREFIX + path, it) }
            }
        }
        return true
    }

    private fun manifest() = BackupManifest(
        formatVersion = BACKUP_FORMAT_VERSION,
        exportedAt = clock.now().toEpochMilliseconds(),
        deviceId = deviceIdProvider.deviceId,
        appVersion = appVersion,
    )

    private fun ZipOutputStream.putJson(name: String, body: String) = putEntry(name, body.encodeToByteArray())

    private fun ZipOutputStream.putEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    // A photo that is gone or unreadable is skipped, since the rest is still worth having; it is read
    // whole first, so a read that fails part-way cannot leave a truncated photo in the archive.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun readPhoto(path: String): ByteArray? = try {
        photoStorage.fileFor(path).takeIf { it.isFile }?.readBytes()
    } catch (e: Exception) {
        null
    }

    private fun discard(target: String) {
        runCatching {
            if (target.startsWith("content://")) {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(target))
            } else {
                File(target).delete()
            }
        }
    }
}

class ZipBackupReader(
    private val context: Context,
    private val photoStorage: AndroidPhotoStorage,
) : BackupReader {

    /**
     * Photo files the archive carries are restored as a side effect, only once the whole archive has
     * been accepted, and only where none is here.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // a malformed archive is not a crash
    override suspend fun read(source: String): BackupReadResult = withContext(Dispatchers.IO) {
        val staging = File(context.filesDir, STAGING_DIR).apply { deleteRecursively() }
        try {
            readArchive(source, staging)
        } catch (e: Exception) {
            BackupReadResult.Rejected(BackupRejection.UNREADABLE)
        } finally {
            staging.deleteRecursively()
        }
    }

    private class Unpacked {
        val texts = mutableMapOf<String, String>()
        val stagedPhotos = mutableMapOf<String, File>()
    }

    private fun readArchive(source: String, staging: File): BackupReadResult {
        val unpacked = context.openRead(source)?.use { unpack(it, staging) }
        val manifest = unpacked?.texts?.get(MANIFEST_ENTRY)?.let { ArchiveJson.decodeFromString<ManifestVersion>(it) }
        val encounters = unpacked?.texts?.get(ENCOUNTERS_ENTRY)
        return when {
            manifest == null -> BackupReadResult.Rejected(BackupRejection.UNREADABLE)
            // Before anything else is looked at, and from the version alone: a newer version's lists, or
            // the rest of its manifest, may not be what this one expects at all.
            manifest.formatVersion > BACKUP_FORMAT_VERSION -> BackupReadResult.Rejected(BackupRejection.TOO_NEW)
            encounters == null -> BackupReadResult.Rejected(BackupRejection.UNREADABLE)
            // This version writes every list, so an archive of its own format that lacks one was cut off.
            manifest.formatVersion == BACKUP_FORMAT_VERSION && !unpacked.texts.keys.containsAll(archiveTextEntries) ->
                BackupReadResult.Rejected(BackupRejection.UNREADABLE)
            else -> {
                val contents = BackupContents(
                    encounters = ArchiveJson.decodeFromString<List<EncounterRecord>>(encounters).map { it.toDomain() },
                    placeCells = unpacked.decoded<PlaceCellRecord>(PLACE_CELLS_ENTRY).map { it.toDomain() },
                    walks = unpacked.decoded<WalkRecord>(WALKS_ENTRY).map { it.toDomain() },
                    trackPoints = unpacked.decoded<TrackPointRecord>(TRACK_POINTS_ENTRY).map { it.toDomain() },
                )
                // fileFor throws for a path outside the photo directory, so such a row is refused, not stored.
                contents.encounters.forEach { cat ->
                    listOfNotNull(cat.photoPath, cat.thumbPath).forEach(photoStorage::fileFor)
                }
                unpacked.stagedPhotos.forEach { (relativePath, staged) -> moveIntoPlace(relativePath, staged) }
                BackupReadResult.Readable(contents)
            }
        }
    }

    private inline fun <reified T> Unpacked.decoded(entry: String): List<T> =
        texts[entry]?.let { ArchiveJson.decodeFromString<List<T>>(it) }.orEmpty()

    private fun unpack(stream: InputStream, staging: File): Unpacked = Unpacked().also { unpacked ->
        ZipInputStream(stream.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when {
                    entry.name in archiveTextEntries -> unpacked.texts[entry.name] = zip.readText()
                    entry.isDirectory -> Unit
                    entry.name.startsWith(PHOTOS_PREFIX) ->
                        stagePhoto(entry.name.removePrefix(PHOTOS_PREFIX), zip, staging, unpacked)
                }
            }
        }
    }

    // fileFor rejects a path that climbs out of the photo directory, which is the whole defence
    // against an archive whose entry names were chosen to overwrite something else.
    private fun stagePhoto(relativePath: String, zip: InputStream, staging: File, unpacked: Unpacked) {
        if (relativePath.isEmpty() || relativePath in unpacked.stagedPhotos) return
        if (photoStorage.fileFor(relativePath).isFile) return
        val staged = File(staging.apply { mkdirs() }, unpacked.stagedPhotos.size.toString())
        staged.outputStream().use { zip.copyTo(it) }
        unpacked.stagedPhotos[relativePath] = staged
    }

    private fun moveIntoPlace(relativePath: String, staged: File) {
        if (photoStorage.fileFor(relativePath).isFile) return
        staged.renameTo(photoStorage.prepare(relativePath))
    }

    private fun ZipInputStream.readText(): String = readBytes().decodeToString()
}

@Serializable
private data class ManifestVersion(val formatVersion: Int)

private val archiveTextEntries =
    setOf(MANIFEST_ENTRY, ENCOUNTERS_ENTRY, PLACE_CELLS_ENTRY, WALKS_ENTRY, TRACK_POINTS_ENTRY)

// Beside the photo directory, not in the cache: a staged photo moves into place by rename.
internal const val STAGING_DIR = "backup-import"
