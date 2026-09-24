package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeSettingsRepository
import dev.catsradar.domain.testing.FakeWalkRecordingState
import dev.catsradar.domain.testing.FakeWalkRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class EndInterruptedWalkTest {

    private val walks = FakeWalkRepository()
    private val settings = FakeSettingsRepository().apply { walking.value = true }
    private val recording = FakeWalkRecordingState(recording = true)
    private val endInterruptedWalk = EndInterruptedWalk(walks, settings, recording, FakeClock(START + 2.hours))

    private suspend fun startWalk() = StartWalk(
        walks,
        FakeIdGenerator(),
        FakeDeviceIdProvider(),
        FakeClock(START),
        analytics = RecordingAnalytics()
    )()

    private fun point(walkId: String, at: Instant) = TrackPoint(walkId, at, 41.3851, 2.1734, 8f)

    @Test
    fun `a recording cut off ends its walk at its last point and turns the mode off`() = runTest {
        val walk = startWalk()
        walks.appendPoint(point(walk.id, START + 10.minutes))
        walks.appendPoint(point(walk.id, START + 25.minutes))

        endInterruptedWalk()

        assertEquals(walk.copy(endedAt = START + 25.minutes, updatedAt = START + 2.hours), walks.walks().single())
        assertFalse(settings.walking.value)
        assertFalse(recording.recording)
    }

    @Test
    fun `a recording cut off before its first point ends the walk at its start`() = runTest {
        startWalk()

        endInterruptedWalk()

        assertEquals(START, walks.walks().single().endedAt)
        assertFalse(settings.walking.value)
    }

    @Test
    fun `a walk that was not recording is left on, and so is the mode`() = runTest {
        recording.recording = false
        startWalk()

        endInterruptedWalk()

        assertNull(walks.walks().single().endedAt)
        assertTrue(settings.walking.value)
    }

    private companion object {
        val START = Instant.parse("2026-09-23T09:00:00Z")
    }
}
