package dev.catsradar.data.repository

import dev.catsradar.data.db.TrackPointEntity
import dev.catsradar.data.db.WalkDao
import dev.catsradar.data.db.WalkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

class FakeWalkDao : WalkDao {
    var observeAllResult: List<WalkEntity> = emptyList()
    var loadOpenResult: WalkEntity? = null
    val inserted = mutableListOf<WalkEntity>()
    var endResult = 1
    var endCall: Pair<String, Instant>? = null
    val insertedPoints = mutableListOf<TrackPointEntity>()
    var lastPointResult: TrackPointEntity? = null
    var lastPointCall: String? = null
    var trackResult: List<TrackPointEntity> = emptyList()
    var trackCall: String? = null

    override fun observeAll(): Flow<List<WalkEntity>> = flowOf(observeAllResult)

    override suspend fun loadOpen(): WalkEntity? = loadOpenResult

    override suspend fun insert(walk: WalkEntity) {
        inserted += walk
        loadOpenResult = walk
    }

    override suspend fun end(id: String, endedAt: Instant): Int {
        endCall = id to endedAt
        return endResult
    }

    override suspend fun insertPoint(point: TrackPointEntity) {
        insertedPoints += point
    }

    override suspend fun loadLastPoint(walkId: String): TrackPointEntity? {
        lastPointCall = walkId
        return lastPointResult
    }

    override fun observeTrack(walkId: String): Flow<List<TrackPointEntity>> {
        trackCall = walkId
        return flowOf(trackResult)
    }
}
