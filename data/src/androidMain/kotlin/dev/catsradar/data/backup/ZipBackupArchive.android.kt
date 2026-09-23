package dev.catsradar.data.backup

import android.content.Context
import android.net.Uri
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupReader
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.platform.BackupWriter
import dev.catsradar.domain.platform.DeviceIdProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupWriter {

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // any I/O failure is the same answer
    override suspend fun write(target: String, contents: BackupContents): Boolean =
        withContext(ioDispatcher) {
            try {
                val stream = context.openWrite(target) ?: return@withContext false
                ZipOutputStream(stream.buffered()).use { zip ->
                    zip.putText(MANIFEST_ENTRY, ArchiveJson.encodeToString(manifest()))
                    zip.putText(
                        ENCOUNTERS_ENTRY,
                        ArchiveJson.encodeToString(contents.encounters.map { it.toRecord() }),
                    )
                    zip.putText(
                        PLACE_CELLS_ENTRY,
                        ArchiveJson.encodeToString(contents.placeCells.map { it.toRecord() }),
                    )
                    zip.putText(WALKS_ENTRY, ArchiveJson.encodeToString(contents.walks.map { it.toRecord() }))
                    zip.putText(
                        TRACK_POINTS_ENTRY,
                        ArchiveJson.encodeToString(contents.trackPoints.map { it.toRecord() }),
                    )
                    contents.encounters.forEach { zip.putPhotos(it.photoPath, it.thumbPath) }
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    private fun manifest() = BackupManifest(
        formatVersion = BACKUP_FORMAT_VERSION,
        exportedAt = clock.now().toEpochMilliseconds(),
        deviceId = deviceIdProvider.deviceId,
        appVersion = appVersion,
    )

    private fun ZipOutputStream.putText(name: String, body: String) {
        putNextEntry(ZipEntry(name))
        write(body.encodeToByteArray())
        closeEntry()
    }

    // A photo that has gone missing since the row was written is skipped, not fatal: the rest of
    // the archive is still worth having, and the row renders a placeholder without it.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun ZipOutputStream.putPhotos(vararg paths: String?) {
        paths.filterNotNull().forEach { path ->
            try {
                val file = photoStorage.fileFor(path)
                if (!file.isFile) return@forEach
                putNextEntry(ZipEntry(PHOTOS_PREFIX + path))
                file.inputStream().use { it.copyTo(this) }
                closeEntry()
            } catch (e: Exception) {
                // Already-added duplicates and unreadable files both land here.
            }
        }
    }
}

class ZipBackupReader(
    private val context: Context,
    private val photoStorage: AndroidPhotoStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupReader {

    /**
     * Photo files the archive carries are restored as a side effect, only once the whole archive has
     * been accepted, and only where none is here.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // a malformed archive is not a crash
    override suspend fun read(source: String): BackupReadResult = withContext(ioDispatcher) {
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
        val manifest = unpacked?.texts?.get(MANIFEST_ENTRY)?.let { ArchiveJson.decodeFromString<BackupManifest>(it) }
        val encounters = unpacked?.texts?.get(ENCOUNTERS_ENTRY)
        return when {
            manifest == null || encounters == null -> BackupReadResult.Rejected(BackupRejection.UNREADABLE)
            // Before any row is decoded: a newer version's rows may not parse in this one at all.
            manifest.formatVersion > BACKUP_FORMAT_VERSION -> BackupReadResult.Rejected(BackupRejection.TOO_NEW)
            else -> {
                val contents = BackupContents(
                    encounters = ArchiveJson.decodeFromString<List<EncounterRecord>>(encounters).map { it.toDomain() },
                    placeCells = unpacked.decoded<PlaceCellRecord>(PLACE_CELLS_ENTRY).map { it.toDomain() },
                    walks = unpacked.decoded<WalkRecord>(WALKS_ENTRY).map { it.toDomain() },
                    trackPoints = unpacked.decoded<TrackPointRecord>(TRACK_POINTS_ENTRY).map { it.toDomain() },
                )
                // fileFor throws for a path outside the photo directory wherever the row is later shown.
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

private val archiveTextEntries =
    setOf(MANIFEST_ENTRY, ENCOUNTERS_ENTRY, PLACE_CELLS_ENTRY, WALKS_ENTRY, TRACK_POINTS_ENTRY)

// Beside the photo directory, not in the cache: a staged photo moves into place by rename.
private const val STAGING_DIR = "backup-import"
