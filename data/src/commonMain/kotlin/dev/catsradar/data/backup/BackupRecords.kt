package dev.catsradar.data.backup

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * An archive whose own version is ahead of this one is refused rather than half-read: its rows may
 * carry fields this build would silently drop on the next export.
 */
internal const val BACKUP_FORMAT_VERSION = 5

internal const val FIRST_FORMAT_WITH_WALKS = 2
internal const val FIRST_FORMAT_WITH_PHOTO_LIST = 4

internal const val MANIFEST_ENTRY = "manifest.json"
internal const val ENCOUNTERS_ENTRY = "encounters.json"
internal const val PLACE_CELLS_ENTRY = "placecells.json"
internal const val ENCOUNTER_PHOTOS_ENTRY = "encounter_photos.json"
internal const val PHOTOS_PREFIX = "photos/"

@Serializable
internal data class BackupManifest(
    val formatVersion: Int,
    val exportedAt: Long,
    val deviceId: String,
    val appVersion: String,
)

// Instants travel as epoch milliseconds and enums as their names: the column names a future
// version might reorder are not what the archive is keyed by.
@Serializable
internal data class EncounterRecord(
    val id: String,
    val occurredAt: Long,
    val tzOffsetMinutes: Int,
    val kind: String,
    val origin: String,
    val locationSource: String,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val coat: String? = null,
    // Written before format 4 only; since then photos travel in their own list.
    val photoPath: String? = null,
    val thumbPath: String? = null,
    val galleryUri: String? = null,
    val sourceMediaUri: String? = null,
    val sourceDigest: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyMeters: Float? = null,
    val locationFixedAt: Long? = null,
    val geohash: String? = null,
    val placeCellId: String? = null,
    val deletedAt: Long? = null,
)

@Serializable
internal data class EncounterPhotoRecord(
    val id: String,
    val encounterId: String,
    val photoPath: String,
    val deviceId: String,
    val addedAt: Long,
    val thumbPath: String? = null,
    val galleryUri: String? = null,
    val sourceMediaUri: String? = null,
    val sourceDigest: String? = null,
)

@Serializable
internal data class PlaceCellRecord(
    val cellId: String,
    val centerLat: Double,
    val centerLon: Double,
    val status: String,
    val attempts: Int,
    val countryCode: String? = null,
    val countryName: String? = null,
    val adminArea: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val lastAttemptAt: Long? = null,
    val resolvedAt: Long? = null,
)

internal fun Encounter.toRecord(): EncounterRecord = EncounterRecord(
    id = id,
    occurredAt = occurredAt.toEpochMilliseconds(),
    tzOffsetMinutes = tzOffsetMinutes,
    kind = kind.name,
    origin = origin.name,
    locationSource = locationSource.name,
    deviceId = deviceId,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
    coat = coat?.name,
    lat = lat,
    lon = lon,
    accuracyMeters = accuracyMeters,
    locationFixedAt = locationFixedAt?.toEpochMilliseconds(),
    geohash = geohash,
    placeCellId = placeCellId,
    deletedAt = deletedAt?.toEpochMilliseconds(),
)

internal fun EncounterRecord.carriedPhotos(): List<EncounterPhoto> = listOfNotNull(
    carriedPhoto(
        encounterId = id,
        deviceId = deviceId,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        photoPath = photoPath,
        thumbPath = thumbPath,
        galleryUri = galleryUri,
        sourceMediaUri = sourceMediaUri,
        sourceDigest = sourceDigest,
    ),
)

internal fun EncounterRecord.toDomain(photos: List<EncounterPhoto>): Encounter = Encounter(
    id = id,
    occurredAt = Instant.fromEpochMilliseconds(occurredAt),
    tzOffsetMinutes = tzOffsetMinutes,
    kind = EncounterKind.valueOf(kind),
    origin = EncounterOrigin.valueOf(origin),
    coat = coat?.let(CatCoat::valueOf),
    photos = photos,
    lat = lat,
    lon = lon,
    accuracyMeters = accuracyMeters,
    locationSource = LocationSource.valueOf(locationSource),
    locationFixedAt = locationFixedAt?.let(Instant::fromEpochMilliseconds),
    geohash = geohash,
    placeCellId = placeCellId,
    deviceId = deviceId,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    deletedAt = deletedAt?.let(Instant::fromEpochMilliseconds),
)

internal fun EncounterPhoto.toRecord(): EncounterPhotoRecord = EncounterPhotoRecord(
    id = id,
    encounterId = encounterId,
    photoPath = photoPath,
    deviceId = deviceId,
    addedAt = addedAt.toEpochMilliseconds(),
    thumbPath = thumbPath,
    galleryUri = galleryUri,
    sourceMediaUri = sourceMediaUri,
    sourceDigest = sourceDigest,
)

internal fun EncounterPhotoRecord.toDomain(): EncounterPhoto = EncounterPhoto(
    id = id,
    encounterId = encounterId,
    photoPath = photoPath,
    thumbPath = thumbPath,
    galleryUri = galleryUri,
    sourceMediaUri = sourceMediaUri,
    sourceDigest = sourceDigest,
    deviceId = deviceId,
    addedAt = Instant.fromEpochMilliseconds(addedAt),
)

internal fun PlaceCell.toRecord(): PlaceCellRecord = PlaceCellRecord(
    cellId = cellId,
    centerLat = centerLat,
    centerLon = centerLon,
    status = status.name,
    attempts = attempts,
    countryCode = countryCode,
    countryName = countryName,
    adminArea = adminArea,
    locality = locality,
    subLocality = subLocality,
    lastAttemptAt = lastAttemptAt?.toEpochMilliseconds(),
    resolvedAt = resolvedAt?.toEpochMilliseconds(),
)

internal fun PlaceCellRecord.toDomain(): PlaceCell = PlaceCell(
    cellId = cellId,
    centerLat = centerLat,
    centerLon = centerLon,
    countryCode = countryCode,
    countryName = countryName,
    adminArea = adminArea,
    locality = locality,
    subLocality = subLocality,
    status = PlaceStatus.valueOf(status),
    attempts = attempts,
    lastAttemptAt = lastAttemptAt?.let(Instant::fromEpochMilliseconds),
    resolvedAt = resolvedAt?.let(Instant::fromEpochMilliseconds),
)
