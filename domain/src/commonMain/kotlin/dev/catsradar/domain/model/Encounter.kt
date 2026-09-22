package dev.catsradar.domain.model

import kotlin.time.Instant

// UtcOffset tops out at +-18:00 (kotlinx-datetime); reject beyond it here, not deep inside localDate().
private const val MAX_TZ_OFFSET_MINUTES = 18 * 60

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
) {
    init {
        require(tzOffsetMinutes in -MAX_TZ_OFFSET_MINUTES..MAX_TZ_OFFSET_MINUTES) {
            "tzOffsetMinutes must be in [-$MAX_TZ_OFFSET_MINUTES, $MAX_TZ_OFFSET_MINUTES], was $tzOffsetMinutes"
        }
    }
}

enum class EncounterKind { TALLY, PHOTO }

enum class EncounterOrigin { APP, WIDGET, CAMERA, GALLERY }

enum class LocationSource { EXIF, CURRENT_FIX, LAST_KNOWN, BACKFILLED, NONE }
