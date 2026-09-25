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
    /** Oldest first; the first is the cat's [cover]. */
    val photos: List<EncounterPhoto> = emptyList(),
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

    val cover: EncounterPhoto? get() = photos.firstOrNull()
}

enum class EncounterKind { TALLY, PHOTO }

enum class EncounterOrigin { APP, WIDGET, NOTIFICATION, CAMERA, GALLERY }

enum class LocationSource { EXIF, CURRENT_FIX, LAST_KNOWN, BACKFILLED, NONE }
