package dev.catsradar.domain.testing

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class FakeWalkRepository :
    WalkRepository,
    RollsBack {
    private val walks = MutableStateFlow<List<Walk>>(emptyList())
    private val points = MutableStateFlow<List<TrackPoint>>(emptyList())
    val upserted = mutableListOf<Walk>()

    override fun checkpoint(): () -> Unit {
        val savedWalks = walks.value
        val savedPoints = points.value
        return {
            walks.value = savedWalks
            points.value = savedPoints
        }
    }

    fun walks(): List<Walk> = walks.value

    fun points(): List<TrackPoint> = points.value

    override fun observeAll(): Flow<List<Walk>> = walks.map { all -> all.sortedByDescending { it.startedAt } }

    override suspend fun openWalk(): Walk? = walks.value.firstOrNull { it.endedAt == null }

    override suspend fun startIfNoneOpen(walk: Walk): Walk =
        openWalk() ?: walk.also { started -> walks.update { it + started } }

    override suspend fun end(id: String, endedAt: Instant, updatedAt: Instant): Boolean {
        val open = walks.value.any { it.id == id && it.endedAt == null }
        walks.update { all ->
            all.map { if (it.id == id && it.endedAt == null) it.copy(endedAt = endedAt, updatedAt = updatedAt) else it }
        }
        return open
    }

    override suspend fun appendPoint(point: TrackPoint) = points.update { it + point }

    override suspend fun lastPoint(walkId: String): TrackPoint? =
        points.value.filter { it.walkId == walkId }.sortedBy { it.at }.lastOrNull()

    override suspend fun loadEveryPoint(): List<TrackPoint> = points.value

    override suspend fun upsert(walk: Walk) {
        upserted += walk
        walks.update { all -> all.filterNot { it.id == walk.id } + walk }
    }

    // Like the database's foreign key, a point is refused unless its walk is already stored.
    override suspend fun appendPoints(points: List<TrackPoint>) {
        require(points.all { point -> walks.value.any { it.id == point.walkId } }) { "a point's walk is not stored" }
        this.points.update { it + points }
    }

    override fun observeTrack(walkId: String): Flow<List<TrackPoint>> =
        points.map { all -> all.filter { it.walkId == walkId }.sortedBy { it.at } }
}
