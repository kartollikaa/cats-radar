package dev.catsradar.domain.usecase

import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeSettingsRepository
import dev.catsradar.domain.testing.FakeWalkRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FollowWalkingModeTest {

    private val walks = FakeWalkRepository()
    private val settings = FakeSettingsRepository()
    private var now = START
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val startWalk = StartWalk(
        walks,
        FakeIdGenerator(),
        FakeDeviceIdProvider(),
        clock,
        analytics = RecordingAnalytics()
    )
    private val followWalkingMode = FollowWalkingMode(
        settings,
        startWalk,
        EndWalk(walks, clock, analytics = RecordingAnalytics())
    )

    @Test
    fun `the mode turning on opens a walk, and turning off ends it`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { followWalkingMode() }

        settings.walking.value = true
        val walk = walks.walks().single()
        assertEquals(START, walk.startedAt)
        assertNull(walk.endedAt)

        now = START + 20.minutes
        settings.walking.value = false
        assertEquals(START + 20.minutes, walks.walks().single().endedAt)
    }

    @Test
    fun `with the mode off, no walk is opened`() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { followWalkingMode() }

        assertTrue(walks.walks().isEmpty())
    }

    @Test
    fun `a process starting with the mode on keeps the walk already open`() = runTest(UnconfinedTestDispatcher()) {
        settings.walking.value = true
        val open = startWalk()
        now = START + 5.minutes

        backgroundScope.launch { followWalkingMode() }

        assertEquals(listOf(open), walks.walks())
    }

    private companion object {
        val START = Instant.parse("2026-09-23T09:00:00Z")
    }
}
