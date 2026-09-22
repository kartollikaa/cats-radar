package dev.catsradar.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TuningTest {
    @Test
    fun `duration constants match the spec`() {
        assertEquals(30.minutes, Tuning.SESSION_GAP)
        assertEquals(5.minutes, Tuning.MIN_RATE_DURATION)
        assertEquals(8.seconds, Tuning.LOCATION_TIMEOUT)
        assertEquals(6.hours, Tuning.LAST_KNOWN_MAX_AGE)
        assertEquals(1.hours, Tuning.RECENT_PHOTO_WINDOW)
        assertEquals(5.seconds, Tuning.UNDO_VISIBLE)
        assertEquals(30.days, Tuning.PURGE_AFTER)
    }

    @Test
    fun `precision and size constants match the spec`() {
        assertEquals(8, Tuning.GEOHASH_PRECISION)
        assertEquals(6, Tuning.PLACE_CELL_PRECISION)
        assertEquals(5, Tuning.AREA_PRECISION)
        assertEquals(2048, Tuning.PHOTO_MAX_SIDE)
        assertEquals(85, Tuning.PHOTO_QUALITY)
        assertEquals(256, Tuning.THUMB_SIZE)
        assertEquals(20, Tuning.GEOCODE_BATCH)
        assertEquals(5, Tuning.MAX_GEOCODE_ATTEMPTS)
        assertEquals(100, Tuning.IMPORT_BATCH_MAX)
    }

    @Test
    fun `milestones match the spec, in order`() {
        assertEquals(listOf(1, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000), Tuning.MILESTONES)
    }
}
