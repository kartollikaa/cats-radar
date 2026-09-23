package dev.catsradar.data.backup

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Instant

internal const val VALID_MANIFEST = """{"formatVersion":1,"exportedAt":0,"deviceId":"d","appVersion":"1"}"""
internal val Epoch = Instant.fromEpochMilliseconds(0)
internal const val NEWER_MANIFEST = """{"formatVersion":99,"exportedAt":0,"deviceId":"d","appVersion":"9"}"""

internal fun catWithPhotos(photoPath: String?, thumbPath: String? = null): String {
    val paths = listOfNotNull(photoPath?.let { "\"photoPath\":\"$it\"" }, thumbPath?.let { "\"thumbPath\":\"$it\"" })
    return """[{"id":"a","occurredAt":0,"tzOffsetMinutes":0,"kind":"PHOTO","origin":"APP",""" +
        """"locationSource":"NONE","deviceId":"d","createdAt":0,"updatedAt":0,${paths.joinToString(",")}}]"""
}

internal fun File.writeArchive(vararg entries: Pair<String, String>) {
    ZipOutputStream(outputStream()).use { zip ->
        entries.forEach { (name, body) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(body.encodeToByteArray())
            zip.closeEntry()
        }
    }
}

internal fun photoCat(photoPath: String) = Encounter(
    id = "cat",
    occurredAt = Epoch,
    tzOffsetMinutes = 0,
    kind = EncounterKind.PHOTO,
    origin = EncounterOrigin.APP,
    coat = null,
    photoPath = photoPath,
    thumbPath = null,
    galleryUri = null,
    sourceDigest = null,
    lat = null,
    lon = null,
    accuracyMeters = null,
    locationSource = LocationSource.NONE,
    locationFixedAt = null,
    geohash = null,
    placeCellId = null,
    deviceId = "d",
    createdAt = Epoch,
    updatedAt = Epoch,
    deletedAt = null,
)
