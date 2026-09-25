package dev.catsradar.data.repository

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import kotlin.time.Instant

// Every field is distinct and non-null where the type allows it: no two same-typed fields
// (lat/lon, the four Instants) share a value, so a transposition between them cannot hide.
internal fun distinctEncounter(): Encounter = Encounter(
    id = "encounter-id-1",
    occurredAt = Instant.parse("2026-01-01T01:00:00Z"),
    tzOffsetMinutes = 60,
    kind = EncounterKind.PHOTO,
    origin = EncounterOrigin.GALLERY,
    coat = CatCoat.GINGER_WHITE,
    photos = listOf(
        EncounterPhoto(
            id = "encounter-id-1",
            encounterId = "encounter-id-1",
            photoPath = "photos/a.jpg",
            thumbPath = "thumbs/a.jpg",
            galleryUri = "content://gallery/1",
            sourceMediaUri = "content://media/external/images/media/1",
            sourceDigest = "digest-1",
            deviceId = "device-1",
            addedAt = Instant.parse("2026-01-01T03:00:00Z"),
        ),
    ),
    lat = 10.111,
    lon = 20.222,
    accuracyMeters = 3.5f,
    locationSource = LocationSource.CURRENT_FIX,
    locationFixedAt = Instant.parse("2026-01-01T02:00:00Z"),
    geohash = "geohash-1",
    placeCellId = "place-1",
    deviceId = "device-1",
    createdAt = Instant.parse("2026-01-01T03:00:00Z"),
    updatedAt = Instant.parse("2026-01-01T04:00:00Z"),
    deletedAt = Instant.parse("2026-01-01T05:00:00Z"),
)

// Every field is distinct: no two same-typed fields (lat/lon, geohash/placeCellId) share a value,
// so a transposition between them cannot hide.
internal fun distinctLocationStamp(): LocationStamp = LocationStamp(
    lat = 11.111,
    lon = 22.222,
    accuracyMeters = 4.5f,
    locationSource = LocationSource.LAST_KNOWN,
    locationFixedAt = Instant.parse("2026-02-01T06:00:00Z"),
    geohash = "geohash-2",
    placeCellId = "place-2",
    updatedAt = Instant.parse("2026-02-01T07:00:00Z"),
)

/** The cat with one photo, the one its row's columns would read back as. */
internal fun Encounter.withPhoto(
    photoPath: String,
    thumbPath: String? = null,
    galleryUri: String? = null,
    sourceMediaUri: String? = null,
    sourceDigest: String? = null,
): Encounter = copy(
    photos = listOf(
        EncounterPhoto(
            id = id,
            encounterId = id,
            photoPath = photoPath,
            thumbPath = thumbPath,
            galleryUri = galleryUri,
            sourceMediaUri = sourceMediaUri,
            sourceDigest = sourceDigest,
            deviceId = deviceId,
            addedAt = createdAt,
        ),
    ),
)
