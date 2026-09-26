package dev.catsradar.data.db

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import kotlin.time.Instant

fun fullEncounterEntity(
    id: String = "encounter-1",
    occurredAt: Instant = Instant.parse("2026-09-20T10:15:00Z"),
    deletedAt: Instant? = null,
): EncounterEntity = EncounterEntity(
    id = id,
    occurredAt = occurredAt,
    tzOffsetMinutes = 180,
    kind = EncounterKind.PHOTO,
    origin = EncounterOrigin.GALLERY,
    coat = CatCoat.GINGER_WHITE,
    lat = 55.751244,
    lon = 37.618423,
    accuracyMeters = 12.5f,
    locationSource = LocationSource.CURRENT_FIX,
    locationFixedAt = Instant.parse("2026-09-20T11:30:00Z"),
    geohash = "ucfv0hg7",
    placeCellId = "ucfv0h",
    deviceId = "device-1",
    createdAt = Instant.parse("2026-09-20T12:45:00Z"),
    updatedAt = Instant.parse("2026-09-20T14:00:00Z"),
    deletedAt = deletedAt,
)

fun photoEntity(
    encounterId: String,
    id: String = encounterId,
    sourceDigest: String? = "digest-1",
    addedAt: Instant = Instant.parse("2026-09-20T12:45:00Z"),
    shotId: String = id,
): EncounterPhotoEntity = EncounterPhotoEntity(
    id = id,
    encounterId = encounterId,
    photoPath = "photos/$id.jpg",
    thumbPath = "thumbs/$id.jpg",
    galleryUri = "content://media/external/images/media/42",
    sourceMediaUri = "content://media/external/images/media/43",
    sourceDigest = sourceDigest,
    deviceId = "device-1",
    addedAt = addedAt,
    shotId = shotId,
)
