package dev.catsradar.data.repository

import dev.catsradar.data.db.TrackPointDao
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
    val upserted = mutableListOf<WalkEntity>()
    var endResult = 1
    var endCall: Triple<String, Instant, Instant>? = null

    override fun observeAll(): Flow<List<WalkEntity>> = flowOf(observeAllResult)

    override suspend fun loadOpen(): WalkEntity? = loadOpenResult

    override fun observeOpen(): Flow<WalkEntity?> = flowOf(loadOpenResult)

    override suspend fun insert(walk: WalkEntity) {
        inserted += walk
        loadOpenResult = walk
    }

    override suspend fun end(id: String, endedAt: Instant, updatedAt: Instant): Int {
        endCall = Triple(id, endedAt, updatedAt)
        return endResult
    }

    override suspend fun upsert(walk: WalkEntity) {
        upserted += walk
    }
}

class FakeTrackPointDao : TrackPointDao {
    val inserted = mutableListOf<TrackPointEntity>()
    var everyResult: List<TrackPointEntity> = emptyList()
    var lastResult: TrackPointEntity? = null
    var lastCall: String? = null
    var trackResult: List<TrackPointEntity> = emptyList()
    var trackCall: String? = null

    override suspend fun insert(point: TrackPointEntity) {
        inserted += point
    }

    override suspend fun insertAll(points: List<TrackPointEntity>) {
        inserted += points
    }

    override suspend fun loadEvery(): List<TrackPointEntity> = everyResult

    override suspend fun loadLast(walkId: String): TrackPointEntity? {
        lastCall = walkId
        return lastResult
    }

    override fun observeTrack(walkId: String): Flow<List<TrackPointEntity>> {
        trackCall = walkId
        return flowOf(trackResult)
    }
}
