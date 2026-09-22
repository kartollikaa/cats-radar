package dev.catsradar.domain.model

import kotlin.time.Instant

data class Encounter(
    val id: String,
    val occurredAt: Instant,
    val tzOffsetMinutes: Int,
    val kind: EncounterKind,
    val origin: EncounterOrigin,
    val coat: CatCoat?,
    val photoPath: String?,
    val thumbPath: String?,
    val galleryUri: String?,
    val sourceDigest: String?,
    val lat: Double?,
    val lon: Double?,
    val accuracyMeters: Float?,
    val locationSource: LocationSource,
    val locationFixedAt: Instant?,
    val geohash: String?,
    val placeCellId: String?,
    val deviceId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

enum class EncounterKind { TALLY, PHOTO }

enum class EncounterOrigin { APP, WIDGET, CAMERA, GALLERY }

enum class LocationSource { EXIF, CURRENT_FIX, LAST_KNOWN, BACKFILLED, NONE }
