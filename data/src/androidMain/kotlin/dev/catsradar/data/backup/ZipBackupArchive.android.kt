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

    /** Photo files the archive carries are restored as a side effect, but only where none is here. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // a malformed archive is not a crash
    override suspend fun read(source: String): BackupReadResult = withContext(ioDispatcher) {
        try {
            readArchive(source)
        } catch (e: Exception) {
            BackupReadResult.Rejected(BackupRejection.UNREADABLE)
        }
    }

    private class Parsed {
        var manifest: BackupManifest? = null
        var encounters: List<EncounterRecord>? = null
        var placeCells: List<PlaceCellRecord> = emptyList()
        var walks: List<WalkRecord> = emptyList()
        var trackPoints: List<TrackPointRecord> = emptyList()
    }

    private fun readArchive(source: String): BackupReadResult {
        val parsed = context.openRead(source)?.use(::parse)
        val manifest = parsed?.manifest
        val encounters = parsed?.encounters
        return when {
            manifest == null || encounters == null -> BackupReadResult.Rejected(BackupRejection.UNREADABLE)
            manifest.formatVersion > BACKUP_FORMAT_VERSION -> BackupReadResult.Rejected(BackupRejection.TOO_NEW)
            else -> BackupReadResult.Readable(
                BackupContents(
                    encounters = encounters.map { it.toDomain() },
                    placeCells = parsed.placeCells.map { it.toDomain() },
                    walks = parsed.walks.map { it.toDomain() },
                    trackPoints = parsed.trackPoints.map { it.toDomain() },
                ),
            )
        }
    }

    private fun parse(stream: InputStream): Parsed = Parsed().also { parsed ->
        ZipInputStream(stream.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when {
                    entry.name == MANIFEST_ENTRY -> parsed.manifest = ArchiveJson.decodeFromString(zip.readText())
                    entry.name == ENCOUNTERS_ENTRY -> parsed.encounters = ArchiveJson.decodeFromString(zip.readText())
                    entry.name == PLACE_CELLS_ENTRY -> parsed.placeCells = ArchiveJson.decodeFromString(zip.readText())
                    entry.name == WALKS_ENTRY -> parsed.walks = ArchiveJson.decodeFromString(zip.readText())
                    entry.name == TRACK_POINTS_ENTRY ->
                        parsed.trackPoints = ArchiveJson.decodeFromString(zip.readText())
                    entry.name.startsWith(PHOTOS_PREFIX) -> restorePhoto(entry.name.removePrefix(PHOTOS_PREFIX), zip)
                }
            }
        }
    }

    // fileFor rejects a path that climbs out of the photo directory, which is the whole defence
    // against an archive whose entry names were chosen to overwrite something else.
    private fun restorePhoto(relativePath: String, stream: InputStream) {
        if (relativePath.isEmpty()) return
        val existing = photoStorage.fileFor(relativePath)
        if (existing.isFile) return
        photoStorage.prepare(relativePath).outputStream().use { stream.copyTo(it) }
    }

    private fun ZipInputStream.readText(): String = readBytes().decodeToString()
}
