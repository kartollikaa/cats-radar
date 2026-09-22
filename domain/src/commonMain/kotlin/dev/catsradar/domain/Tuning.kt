package dev.catsradar.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Tuning is the single named home for these literals (spec §8); MagicNumber's job of keeping
// numbers out of call sites is exactly what declaring them here already does.
@Suppress("MagicNumber")
object Tuning {
    val SESSION_GAP: Duration = 30.minutes
    val MIN_RATE_DURATION: Duration = 5.minutes
    val LOCATION_TIMEOUT: Duration = 8.seconds
    val LAST_KNOWN_MAX_AGE: Duration = 6.hours
    val RECENT_PHOTO_WINDOW: Duration = 1.hours
    val UNDO_VISIBLE: Duration = 5.seconds
    val PURGE_AFTER: Duration = 30.days

    const val PLACE_CELL_PRECISION: Int = 6
    const val AREA_PRECISION: Int = 5
    const val PHOTO_MAX_SIDE: Int = 2048
    const val PHOTO_QUALITY: Int = 85
    const val THUMB_SIZE: Int = 256
    const val GEOCODE_BATCH: Int = 20
    const val MAX_GEOCODE_ATTEMPTS: Int = 5
    const val IMPORT_BATCH_MAX: Int = 100

    val MILESTONES: List<Int> = listOf(1, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000)
}
