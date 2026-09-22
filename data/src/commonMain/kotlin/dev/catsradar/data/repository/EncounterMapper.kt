package dev.catsradar.data.repository

import dev.catsradar.data.db.EncounterEntity
import dev.catsradar.domain.model.Encounter

internal fun EncounterEntity.toDomain(): Encounter =
    Encounter(
        id = id,
        occurredAt = occurredAt,
        tzOffsetMinutes = tzOffsetMinutes,
        kind = kind,
        origin = origin,
        coat = coat,
        photoPath = photoPath,
        thumbPath = thumbPath,
        galleryUri = galleryUri,
        sourceDigest = sourceDigest,
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
        photoPath = photoPath,
        thumbPath = thumbPath,
        galleryUri = galleryUri,
        sourceDigest = sourceDigest,
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
