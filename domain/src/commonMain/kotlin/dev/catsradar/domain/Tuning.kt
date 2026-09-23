package dev.catsradar.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Suppress("MagicNumber") // every literal below is already a named constant, not one scattered at a call site
object Tuning {
    val SESSION_GAP: Duration = 30.minutes
    val MIN_RATE_DURATION: Duration = 5.minutes
    val LOCATION_TIMEOUT: Duration = 8.seconds
    val LAST_KNOWN_MAX_AGE: Duration = 6.hours
    val RECENT_PHOTO_WINDOW: Duration = 1.hours
    val UNDO_VISIBLE: Duration = 5.seconds

    /** How long the "+N" burst stays up after the last tap; long enough to read, short enough not to linger. */
    val TAP_BURST_VISIBLE: Duration = 1200.milliseconds
    val PURGE_AFTER: Duration = 30.days

    const val GEOHASH_PRECISION: Int = 8
    const val PLACE_CELL_PRECISION: Int = 6
    const val AREA_PRECISION: Int = 5
    const val PHOTO_MAX_SIDE: Int = 2048
    const val PHOTO_QUALITY: Int = 85
    const val THUMB_SIZE: Int = 256
    const val GEOCODE_BATCH: Int = 20
    const val MAX_GEOCODE_ATTEMPTS: Int = 5
    const val IMPORT_BATCH_MAX: Int = 100

    /** A fix less precise than this is left out of a walk's route rather than bending it. */
    const val TRACK_MAX_ACCURACY_METERS: Float = 50f

    /** A fix nearer than this to the route's last point adds nothing to the route. */
    const val TRACK_MIN_STEP_METERS: Double = 10.0

    val MILESTONES: List<Int> = listOf(1, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000)
}
