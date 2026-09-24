package dev.catsradar.presentation

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/** A read-only stand-in: reads return what it was built with, and every write throws. */
internal class StoredWalkRepository(
    private val walks: List<Walk> = emptyList(),
    private val points: List<TrackPoint> = emptyList(),
) : WalkRepository {

    override fun observeAll(): Flow<List<Walk>> = flowOf(walks.sortedByDescending { it.startedAt })

    override suspend fun openWalk(): Walk? = walks.firstOrNull { it.endedAt == null }

    override suspend fun startIfNoneOpen(walk: Walk): Walk = error("read-only")

    override suspend fun end(id: String, endedAt: Instant, updatedAt: Instant): Boolean = error("read-only")

    override suspend fun appendPoint(point: TrackPoint): Unit = error("read-only")

    override suspend fun lastPoint(walkId: String): TrackPoint? =
        points.filter { it.walkId == walkId }.maxByOrNull { it.at }

    override fun observeTrack(walkId: String): Flow<List<TrackPoint>> =
        flowOf(points.filter { it.walkId == walkId }.sortedBy { it.at })

    override suspend fun loadEveryPoint(): List<TrackPoint> = points

    override fun observeEveryPoint(): Flow<List<TrackPoint>> =
        flowOf(points.sortedWith(compareBy({ it.walkId }, { it.at })))

    override suspend fun upsert(walk: Walk): Unit = error("read-only")

    override suspend fun appendPoints(points: List<TrackPoint>): Unit = error("read-only")
}
