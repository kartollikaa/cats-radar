package dev.catsradar.data.backup

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal const val WALKS_ENTRY = "walks.json"
internal const val TRACK_POINTS_ENTRY = "trackpoints.json"

@Serializable
internal data class WalkRecord(
    val id: String,
    val startedAt: Long,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val endedAt: Long? = null,
)

@Serializable
internal data class TrackPointRecord(
    val walkId: String,
    val at: Long,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
)

internal fun Walk.toRecord(): WalkRecord = WalkRecord(
    id = id,
    startedAt = startedAt.toEpochMilliseconds(),
    deviceId = deviceId,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
    endedAt = endedAt?.toEpochMilliseconds(),
)

internal fun WalkRecord.toDomain(): Walk = Walk(
    id = id,
    startedAt = Instant.fromEpochMilliseconds(startedAt),
    endedAt = endedAt?.let(Instant::fromEpochMilliseconds),
    deviceId = deviceId,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

internal fun TrackPoint.toRecord(): TrackPointRecord = TrackPointRecord(
    walkId = walkId,
    at = at.toEpochMilliseconds(),
    lat = lat,
    lon = lon,
    accuracyMeters = accuracyMeters,
)

internal fun TrackPointRecord.toDomain(): TrackPoint = TrackPoint(
    walkId = walkId,
    at = Instant.fromEpochMilliseconds(at),
    lat = lat,
    lon = lon,
    accuracyMeters = accuracyMeters,
)
