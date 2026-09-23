package dev.catsradar.data.db

import kotlin.time.Instant

val walkStart: Instant = Instant.parse("2026-09-23T09:00:00Z")

fun walkEntity(id: String, startedAt: Instant = walkStart) = WalkEntity(
    id = id,
    startedAt = startedAt,
    endedAt = null,
    deviceId = "device",
    createdAt = startedAt,
    updatedAt = startedAt,
)

fun trackPointEntity(walkId: String, second: Long, lat: Double = 41.0) = TrackPointEntity(
    walkId = walkId,
    at = Instant.fromEpochSeconds(walkStart.epochSeconds + second),
    lat = lat,
    lon = 2.0,
    accuracyMeters = 5f,
)
