package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.repository.WalkRepository
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeWalkRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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

        assertEquals(walk.copy(endedAt = START + 30.minutes, updatedAt = START + 30.minutes), ended)
        assertEquals(ended, repository.walks().single())
        assertNull(EndWalk(repository, FakeClock(START + 31.minutes))())

        startWalk()
        assertEquals(2, repository.walks().size)
    }

    @Test
    fun `a clock set back before the start ends the walk at its start`() = runTest {
        startWalk()

        val ended = EndWalk(repository, FakeClock(START - 5.minutes))()

        assertEquals(START, assertNotNull(ended).endedAt)
        assertEquals(START - 5.minutes, ended.updatedAt)
        assertEquals(ended, repository.walks().single())
    }

    @Test
    fun `an end another call already made is not reported as this one's`() = runTest {
        val walk = startWalk()
        EndWalk(repository, FakeClock(START + 30.minutes))()
        val stale = object : WalkRepository by repository {
            override suspend fun openWalk() = walk
        }

        assertNull(EndWalk(stale, FakeClock(START + 31.minutes))())
        assertEquals(START + 30.minutes, repository.walks().single().endedAt)
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
    fun `a step is never shorter than either fix is rough, so jitter standing still adds nothing`() = runTest {
        startWalk()
        recordTrackPoint(fix(atSecond = 5, accuracy = 20f))

        assertFalse(recordTrackPoint(fix(atSecond = 10, latOffset = 0.00015, accuracy = 5f)))
        assertTrue(recordTrackPoint(fix(atSecond = 15, latOffset = 0.0002, accuracy = 5f)))
        assertFalse(recordTrackPoint(fix(atSecond = 20, latOffset = 0.00035, accuracy = 20f)))
        assertTrue(recordTrackPoint(fix(atSecond = 25, latOffset = 0.00035, accuracy = 5f)))
    }

    @Test
    fun `two fixes arriving together are measured one after the other`() = runTest {
        startWalk()
        recordTrackPoint(fix(atSecond = 5))
        val slowReads = object : WalkRepository by repository {
            override suspend fun lastPoint(walkId: String) = repository.lastPoint(walkId).also { yield() }
        }
        val record = RecordTrackPoint(slowReads)

        val kept = listOf(
            async { record(fix(atSecond = 10, latOffset = 0.0002)) },
            async { record(fix(atSecond = 11, latOffset = 0.00025)) },
        ).awaitAll()

        assertEquals(listOf(true, false), kept)
        assertEquals(2, repository.points().size)
    }

    @Test
    fun `a fix from before the walk is left out, one from its first moment is kept`() = runTest {
        startWalk()

        assertFalse(recordTrackPoint(fix(atSecond = -1)))
        assertTrue(recordTrackPoint(fix(atSecond = 0)))
    }

    @Test
    fun `a fix older than the route's last point is left out`() = runTest {
        startWalk()
        recordTrackPoint(fix(atSecond = 60))

        assertFalse(recordTrackPoint(fix(atSecond = 30, latOffset = 0.01)))
        assertEquals(1, repository.points().size)
    }

    private companion object {
        val START = Instant.parse("2026-09-23T09:00:00Z")
    }
}
