package dev.catsradar.data.repository

import dev.catsradar.data.db.TrackPointDao
import dev.catsradar.data.db.TrackPointEntity
import dev.catsradar.data.db.WalkDao
import dev.catsradar.data.db.WalkEntity
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class WalkRepositoryImpl(private val dao: WalkDao, private val points: TrackPointDao) : WalkRepository {
    override fun observeAll(): Flow<List<Walk>> = dao.observeAll().map { walks -> walks.map { it.toDomain() } }

    override suspend fun openWalk(): Walk? = dao.loadOpen()?.toDomain()

    override fun observeOpen(): Flow<Walk?> = dao.observeOpen().map { it?.toDomain() }

    override suspend fun startIfNoneOpen(walk: Walk): Walk = dao.startIfNoneOpen(walk.toEntity()).toDomain()

    override suspend fun end(id: String, endedAt: Instant, updatedAt: Instant): Boolean =
        dao.end(id, endedAt, updatedAt) > 0

    override suspend fun appendPoint(point: TrackPoint) = points.insert(point.toEntity())

    override suspend fun lastPoint(walkId: String): TrackPoint? = points.loadLast(walkId)?.toDomain()

    override fun observeTrack(walkId: String): Flow<List<TrackPoint>> =
        points.observeTrack(walkId).map { track -> track.map { it.toDomain() } }

    override suspend fun loadEveryPoint(): List<TrackPoint> = points.loadEvery().map { it.toDomain() }

    override suspend fun upsert(walk: Walk) = dao.upsert(walk.toEntity())

    override suspend fun appendPoints(points: List<TrackPoint>) = this.points.insertAll(points.map { it.toEntity() })
}

private fun WalkEntity.toDomain() = Walk(id, startedAt, endedAt, deviceId, createdAt, updatedAt)

private fun Walk.toEntity() = WalkEntity(id, startedAt, endedAt, deviceId, createdAt, updatedAt)

private fun TrackPointEntity.toDomain() = TrackPoint(walkId, at, lat, lon, accuracyMeters)

private fun TrackPoint.toEntity() = TrackPointEntity(
    walkId = walkId,
    at = at,
    lat = lat,
    lon = lon,
    accuracyMeters = accuracyMeters,
)
