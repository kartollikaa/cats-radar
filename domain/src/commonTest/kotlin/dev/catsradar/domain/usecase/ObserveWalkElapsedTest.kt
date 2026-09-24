package dev.catsradar.domain.usecase

import app.cash.turbine.test
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeWalkRepository
import dev.catsradar.domain.testing.MovableClock
import dev.catsradar.domain.testing.RecordingAnalytics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val Start = Instant.parse("2026-09-24T10:00:00Z")

class ObserveWalkElapsedTest {

    private val walks = FakeWalkRepository()

    private suspend fun startWalkAt(at: Instant) {
        StartWalk(walks, FakeIdGenerator(), FakeDeviceIdProvider(), FakeClock(at), analytics = RecordingAnalytics())()
    }

    @Test
    fun `with no walk on there is no walk time`() = runTest {
        ObserveWalkElapsed(ObserveOpenWalk(walks), FakeClock(Start), ticks = flowOf(Unit))().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a walk on is measured from its start to now`() = runTest {
        startWalkAt(Start)

        ObserveWalkElapsed(ObserveOpenWalk(walks), FakeClock(Start + 32.minutes), ticks = flowOf(Unit))().test {
            assertEquals(32.minutes, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the walk time moves on each tick with nothing written`() = runTest {
        startWalkAt(Start)
        val clock = MovableClock(Start + 1.minutes)
        val ticks = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

        ObserveWalkElapsed(ObserveOpenWalk(walks), clock, ticks)().test {
            assertEquals(1.minutes, awaitItem())

            clock.now = Start + 2.minutes
            ticks.emit(Unit)

            assertEquals(2.minutes, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a walk that starts or ends is followed`() = runTest {
        val clock = MovableClock(Start)

        ObserveWalkElapsed(ObserveOpenWalk(walks), clock, ticks = flowOf(Unit))().test {
            assertNull(awaitItem())

            startWalkAt(Start)
            assertEquals(Duration.ZERO, awaitItem())

            clock.now = Start + 5.minutes
            EndWalk(walks, clock, analytics = RecordingAnalytics())()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // A clock set back after the walk started would otherwise report a negative length.
    @Test
    fun `a start ahead of the clock reads as no time rather than negative`() = runTest {
        startWalkAt(Start + 10.minutes)

        ObserveWalkElapsed(ObserveOpenWalk(walks), FakeClock(Start), ticks = flowOf(Unit))().test {
            assertEquals(Duration.ZERO, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
