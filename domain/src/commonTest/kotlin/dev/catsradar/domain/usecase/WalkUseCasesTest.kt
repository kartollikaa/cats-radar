package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeWalkRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WalkUseCasesTest {

    private val repository = FakeWalkRepository()
    private val startWalk = StartWalk(repository, FakeIdGenerator(), FakeDeviceIdProvider(), FakeClock(START))
    private val recordTrackPoint = RecordTrackPoint(repository)

    // About eleven metres apart: one step of a walk.
    private fun fix(atSecond: Int, latOffset: Double = 0.0, accuracy: Float = 8f) =
        LocationFix(41.3851 + latOffset, 2.1734, accuracy, START + atSecond.seconds)

    @Test
    fun `starting a walk twice is one walk`() = runTest {
        val first = startWalk()
        val second = startWalk()

        assertEquals(first, second)
        assertEquals(1, repository.walks().size)
        assertNull(first.endedAt)
    }

    @Test
    fun `ending ends the walk that is on, and a walk can start again afterwards`() = runTest {
        val walk = startWalk()

        val ended = EndWalk(repository, FakeClock(START + 30.minutes))()

        assertEquals(START + 30.minutes, assertNotNull(ended).endedAt)
        assertEquals(START + 30.minutes, repository.walks().single().endedAt)
        assertEquals(walk.id, ended.id)
        assertNull(EndWalk(repository, FakeClock(START + 31.minutes))())

        startWalk()
        assertEquals(2, repository.walks().size)
    }

    @Test
    fun `a fix goes on the route of the walk that is on`() = runTest {
        val walk = startWalk()

        assertTrue(recordTrackPoint(fix(atSecond = 5)))

        val point = repository.points().single()
        assertEquals(walk.id, point.walkId)
        assertEquals(START + 5.seconds, point.at)
    }

    @Test
    fun `with no walk on, a fix is not recorded`() = runTest {
        assertFalse(recordTrackPoint(fix(atSecond = 5)))
        assertTrue(repository.points().isEmpty())
    }

    @Test
    fun `a fix too rough to trust is left out`() = runTest {
        startWalk()

        assertFalse(recordTrackPoint(fix(atSecond = 5, accuracy = Tuning.TRACK_MAX_ACCURACY_METERS + 1f)))
        assertTrue(recordTrackPoint(fix(atSecond = 6, accuracy = Tuning.TRACK_MAX_ACCURACY_METERS)))
    }

    @Test
    fun `a fix a step or more from the last point is kept, one nearer is left out`() = runTest {
        startWalk()
        recordTrackPoint(fix(atSecond = 5))

        assertFalse(recordTrackPoint(fix(atSecond = 10, latOffset = 0.00005)))
        assertTrue(recordTrackPoint(fix(atSecond = 15, latOffset = 0.0001)))
        assertEquals(2, repository.points().size)
    }

    @Test
    fun `a fix older than the walk or than the route's last point is left out`() = runTest {
        startWalk()
        recordTrackPoint(fix(atSecond = 60))

        assertFalse(recordTrackPoint(LocationFix(41.0, 2.0, 5f, START - 1.seconds)))
        assertFalse(recordTrackPoint(fix(atSecond = 30, latOffset = 0.01)))
        assertEquals(1, repository.points().size)
    }

    private companion object {
        val START = Instant.parse("2026-09-23T09:00:00Z")
    }
}
