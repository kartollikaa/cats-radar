package dev.catsradar.data.repository

import dev.catsradar.data.db.EncounterEntity
import dev.catsradar.data.db.EncounterPhotoEntity
import dev.catsradar.data.db.EncounterWithPhotos
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.oldestFirst

// Must match Encounter's private bound (UtcOffset tops out at +-18:00). A row outside it is
// clamped, not thrown: Encounter's init would otherwise reject every row read after this one too.
private const val MAX_TZ_OFFSET_MINUTES = 18 * 60

internal fun EncounterWithPhotos.toDomain(): Encounter =
    encounter.toDomain(photos = photos.map { it.toDomain() }.oldestFirst())

internal fun EncounterEntity.toDomain(photos: List<EncounterPhoto> = emptyList()): Encounter =
    Encounter(
        id = id,
        occurredAt = occurredAt,
        tzOffsetMinutes = tzOffsetMinutes.coerceIn(-MAX_TZ_OFFSET_MINUTES, MAX_TZ_OFFSET_MINUTES),
        kind = kind,
        origin = origin,
        coat = coat,
        photos = photos,
        lat = lat,
        lon = lon,
        accuracyMeters = accuracyMeters,
        locationSource = locationSource,
        locationFixedAt = locationFixedAt,
        geohash = geohash,
        placeCellId = placeCellId,
        deviceId = deviceId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

internal fun Encounter.toEntity(): EncounterEntity =
    EncounterEntity(
        id = id,
        occurredAt = occurredAt,
        tzOffsetMinutes = tzOffsetMinutes,
        kind = kind,
        origin = origin,
        coat = coat,
        lat = lat,
        lon = lon,
        accuracyMeters = accuracyMeters,
        locationSource = locationSource,
        locationFixedAt = locationFixedAt,
        geohash = geohash,
        placeCellId = placeCellId,
        deviceId = deviceId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

internal fun EncounterPhotoEntity.toDomain(): EncounterPhoto = EncounterPhoto(
    id = id,
    encounterId = encounterId,
    photoPath = photoPath,
    thumbPath = thumbPath,
    galleryUri = galleryUri,
    sourceMediaUri = sourceMediaUri,
    sourceDigest = sourceDigest,
    deviceId = deviceId,
    addedAt = addedAt,
    shotId = shotId,
)

internal fun EncounterPhoto.toEntity(): EncounterPhotoEntity = EncounterPhotoEntity(
    id = id,
    encounterId = encounterId,
    photoPath = photoPath,
    thumbPath = thumbPath,
    galleryUri = galleryUri,
    sourceMediaUri = sourceMediaUri,
    sourceDigest = sourceDigest,
    deviceId = deviceId,
    addedAt = addedAt,
    shotId = shotId,
)
