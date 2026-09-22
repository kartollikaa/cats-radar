package dev.catsradar.domain.time

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Clock
import kotlin.time.Instant

class LocalDateExtensionsTest {
    @Test
    fun `same instant with offsets +14h00 and -11h00 falls on different local dates`() {
        val occurredAt = Instant.parse("2026-09-21T23:00:00Z")
        val ahead = encounterAt(occurredAt, tzOffsetMinutes = 14 * 60)
        val behind = encounterAt(occurredAt, tzOffsetMinutes = -11 * 60)

        assertNotEquals(ahead.localDate(), behind.localDate())
        assertEquals(LocalDate.parse("2026-09-22"), ahead.localDate())
        assertEquals(LocalDate.parse("2026-09-21"), behind.localDate())
    }

    @Test
    fun `today reads the date from the injected clock and zone`() {
        val fixedClock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-01-01T00:30:00Z")
        }

        assertEquals(LocalDate.parse("2026-01-01"), fixedClock.today(TimeZone.UTC))
    }

    private fun encounterAt(instant: Instant, tzOffsetMinutes: Int): Encounter = Encounter(
        id = "id",
        occurredAt = instant,
        tzOffsetMinutes = tzOffsetMinutes,
        kind = EncounterKind.TALLY,
        origin = EncounterOrigin.APP,
        coat = null,
        photoPath = null,
        thumbPath = null,
        galleryUri = null,
        sourceDigest = null,
        lat = null,
        lon = null,
        accuracyMeters = null,
        locationSource = LocationSource.NONE,
        locationFixedAt = null,
        geohash = null,
        placeCellId = null,
        deviceId = "device",
        createdAt = instant,
        updatedAt = instant,
        deletedAt = null,
    )
}
