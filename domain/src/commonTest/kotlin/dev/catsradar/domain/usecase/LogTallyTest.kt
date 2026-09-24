package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.RecordingAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class LogTallyTest {
    private val now = Instant.parse("2026-09-22T10:00:00Z")
    private val repository = FakeEncounterRepository()

    @Test
    fun `logs a TALLY encounter from APP with no location`() = runTest {
        val logTally = LogTally(
            repository,
            FakeIdGenerator(),
            FakeDeviceIdProvider(),
            FakeClock(now),
            analytics = RecordingAnalytics(),
            TimeZone.UTC
        )

        val encounter = logTally()

        assertEquals(EncounterKind.TALLY, encounter.kind)
        assertEquals(EncounterOrigin.APP, encounter.origin)
        assertEquals(LocationSource.NONE, encounter.locationSource)
        assertNull(encounter.coat)
        assertNull(encounter.lat)
        assertNull(encounter.lon)
        assertNull(encounter.accuracyMeters)
        assertNull(encounter.locationFixedAt)
        assertNull(encounter.geohash)
        assertNull(encounter.placeCellId)
        assertNull(encounter.photoPath)
        assertNull(encounter.thumbPath)
        assertNull(encounter.galleryUri)
        assertNull(encounter.sourceDigest)
    }

    @Test
    fun `stamps occurredAt createdAt and updatedAt from the clock, and deviceId from the provider`() = runTest {
        val logTally = LogTally(
            repository,
            FakeIdGenerator(),
            FakeDeviceIdProvider("device-42"),
            FakeClock(now),
            analytics = RecordingAnalytics(),
            TimeZone.UTC,
        )

        val encounter = logTally()

        assertEquals(now, encounter.occurredAt)
        assertEquals(now, encounter.createdAt)
        assertEquals(now, encounter.updatedAt)
        assertEquals("device-42", encounter.deviceId)
    }

    @Test
    fun `computes the timezone offset in minutes from the injected zone`() = runTest {
        val logTally = LogTally(
            repository,
            FakeIdGenerator(),
            FakeDeviceIdProvider(),
            FakeClock(now),
            analytics = RecordingAnalytics(),
            UtcOffset(hours = 3).asTimeZone(),
        )

        val encounter = logTally()

        assertEquals(180, encounter.tzOffsetMinutes)
    }

    @Test
    fun `inserts the encounter and returns the same one so the caller can target an undo`() = runTest {
        val logTally = LogTally(
            repository,
            FakeIdGenerator(),
            FakeDeviceIdProvider(),
            FakeClock(now),
            analytics = RecordingAnalytics(),
            TimeZone.UTC
        )

        val encounter = logTally()

        assertEquals(listOf(encounter), repository.inserted)
    }

    @Test
    fun `each invocation gets a fresh id`() = runTest {
        val logTally = LogTally(
            repository,
            FakeIdGenerator(),
            FakeDeviceIdProvider(),
            FakeClock(now),
            analytics = RecordingAnalytics(),
            TimeZone.UTC
        )

        val first = logTally()
        val second = logTally()

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `invoke reaches insert with no suspension before it`() = runTest {
        val logTally = LogTally(
            repository,
            FakeIdGenerator(),
            FakeDeviceIdProvider(),
            FakeClock(now),
            analytics = RecordingAnalytics(),
            TimeZone.UTC
        )

        // Dispatchers.Unconfined runs eagerly to the first real suspension point; if invoke()
        // awaited anything (the device id, in particular) before insert(), the repository would
        // still be empty here.
        launch(Dispatchers.Unconfined) { logTally() }

        assertEquals(1, repository.inserted.size)
    }
}
