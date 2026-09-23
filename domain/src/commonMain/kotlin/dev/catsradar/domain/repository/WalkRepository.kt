package dev.catsradar.domain.repository

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface WalkRepository {
    /** Newest start first. */
    fun observeAll(): Flow<List<Walk>>

    /** The walk that has not ended, or null; there is never more than one. */
    suspend fun openWalk(): Walk?

    /** Starts [walk] unless a walk is already open, atomically; returns whichever walk is now open. */
    suspend fun startIfNoneOpen(walk: Walk): Walk

    /**
     * Ends walk [id] at [endedAt] if it is still on, recording [updatedAt] as when it changed; false
     * when it had already ended or does not exist.
     */
    suspend fun end(id: String, endedAt: Instant, updatedAt: Instant): Boolean

    suspend fun appendPoint(point: TrackPoint)

    suspend fun lastPoint(walkId: String): TrackPoint?

    fun observeTrack(walkId: String): Flow<List<TrackPoint>>
}
