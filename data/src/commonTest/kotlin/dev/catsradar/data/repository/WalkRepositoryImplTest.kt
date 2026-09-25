package dev.catsradar.data.repository

import dev.catsradar.data.db.TrackPointEntity
import dev.catsradar.data.db.WalkEntity
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class WalkRepositoryImplTest {
    private val dao = FakeWalkDao()
    private val points = FakeTrackPointDao()
    private val repository = WalkRepositoryImpl(dao, points)

    @Test
    fun startIfNoneOpenMapsEveryFieldBothWays() = runTest {
        val started = repository.startIfNoneOpen(distinctWalk)

        assertEquals(listOf(distinctWalkEntity), dao.inserted)
        assertEquals(distinctWalk, started)
    }

    @Test
    fun observeAllAndOpenWalkMapEveryField() = runTest {
        dao.observeAllResult = listOf(distinctWalkEntity)
        dao.loadOpenResult = distinctWalkEntity

        assertEquals(listOf(distinctWalk), repository.observeAll().first())
        assertEquals(distinctWalk, repository.openWalk())
    }

    @Test
    fun endReportsWhetherAWalkWasStillOn() = runTest {
        assertTrue(repository.end("walk", endedAt = at(5), updatedAt = at(6)))
        assertEquals(Triple("walk", at(5), at(6)), dao.endCall)

        dao.endResult = 0
        assertFalse(repository.end("walk", endedAt = at(7), updatedAt = at(7)))
    }

    @Test
    fun appendPointMapsEveryField() = runTest {
        repository.appendPoint(distinctPoint)

        assertEquals(listOf(distinctPointEntity), points.inserted)
    }

    @Test
    fun lastPointAndTrackMapEveryField() = runTest {
        points.lastResult = distinctPointEntity.copy(rowId = 7)
        points.trackResult = listOf(distinctPointEntity.copy(rowId = 7))

        assertEquals(distinctPoint, repository.lastPoint("walk"))
        assertEquals(listOf(distinctPoint), repository.observeTrack("walk").first())
        assertEquals("walk", points.lastCall)
        assertEquals("walk", points.trackCall)
    }

    @Test
    fun upsertAppendPointsAndLoadEveryPointMapEveryField() = runTest {
        points.everyResult = listOf(distinctPointEntity.copy(rowId = 7))

        repository.upsert(distinctWalk)
        repository.appendPoints(listOf(distinctPoint))

        assertEquals(listOf(distinctWalkEntity), dao.upserted)
        assertEquals(listOf(distinctPointEntity), points.inserted)
        assertEquals(listOf(distinctPoint), repository.loadEveryPoint())
    }

    @Test
    fun observeEveryPointMapsEveryField() = runTest {
        points.everyResult = listOf(distinctPointEntity.copy(rowId = 7))

        assertEquals(listOf(distinctPoint), repository.observeEveryPoint().first())
    }

    private companion object {
        fun at(second: Long) = Instant.fromEpochSeconds(1_790_000_000 + second)

        val distinctWalk = Walk("walk", at(1), at(2), "device", at(3), at(4))
        val distinctWalkEntity = WalkEntity("walk", at(1), at(2), "device", at(3), at(4))
        val distinctPoint = TrackPoint("walk", at(1), lat = 41.5, lon = 2.25, accuracyMeters = 7.5f)
        val distinctPointEntity =
            TrackPointEntity(walkId = "walk", at = at(1), lat = 41.5, lon = 2.25, accuracyMeters = 7.5f)
    }
}
