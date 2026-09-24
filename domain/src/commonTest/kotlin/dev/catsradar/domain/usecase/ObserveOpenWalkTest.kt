package dev.catsradar.domain.usecase

import app.cash.turbine.test
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeWalkRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val Start = Instant.parse("2026-09-24T10:00:00Z")

class ObserveOpenWalkTest {

    private val walks = FakeWalkRepository()
    private val ids = FakeIdGenerator()
    private val startWalk = StartWalk(
        walks,
        ids,
        FakeDeviceIdProvider(),
        FakeClock(Start),
        analytics = RecordingAnalytics()
    )

    @Test
    fun `follows a walk from its start to its end`() = runTest {
        ObserveOpenWalk(walks)().test {
            assertNull(awaitItem())

            val started = startWalk()
            assertEquals(started, awaitItem())

            EndWalk(walks, FakeClock(Start + 30.minutes), analytics = RecordingAnalytics())()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a change to a walk already over is not news`() = runTest {
        val over = startWalk().also { EndWalk(walks, FakeClock(Start + 30.minutes), RecordingAnalytics())() }
        val open = StartWalk(
            walks,
            ids,
            FakeDeviceIdProvider(),
            FakeClock(Start + 1.hours),
            analytics = RecordingAnalytics()
        )()

        ObserveOpenWalk(walks)().test {
            assertEquals(open, awaitItem())

            walks.upsert(walks.walks().first { it.id == over.id }.copy(updatedAt = Start + 2.hours))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
