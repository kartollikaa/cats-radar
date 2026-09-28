package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal class FakeWalkRepository : WalkRepository {
    private val open = MutableStateFlow<Walk?>(null)

    fun startAt(at: Instant) {
        open.value = Walk(
            id = "walk-1",
            startedAt = at,
            endedAt = null,
            deviceId = "device-1",
            createdAt = at,
            updatedAt = at,
        )
    }

    override suspend fun openWalk(): Walk? = open.value
    override fun observeAll(): Flow<List<Walk>> = open.map { listOfNotNull(it) }
    override suspend fun startIfNoneOpen(walk: Walk): Walk = open.value ?: walk.also { open.value = it }

    override suspend fun end(id: String, endedAt: Instant, updatedAt: Instant): Boolean {
        val ending = open.value?.takeIf { it.id == id } ?: return false
        open.value = null
        return ending.endedAt == null
    }

    override suspend fun appendPoint(point: TrackPoint) = Unit
    override suspend fun lastPoint(walkId: String): TrackPoint? = null
    override fun observeTrack(walkId: String): Flow<List<TrackPoint>> = flowOf(emptyList())
    override suspend fun loadEveryPoint(): List<TrackPoint> = emptyList()
    override fun observeEveryPoint(): Flow<List<TrackPoint>> = flowOf(emptyList())
    override suspend fun upsert(walk: Walk) = Unit
    override suspend fun appendPoints(points: List<TrackPoint>) = Unit
}
