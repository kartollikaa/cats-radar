package dev.catsradar.domain.model

import kotlin.time.Instant

/** A walk the user started; it is on until [endedAt] is set. Its route is its [TrackPoint]s. */
data class Walk(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val deviceId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TrackPoint(
    val walkId: String,
    val at: Instant,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
)

/** A walk with its route, [points] in the order they were recorded. */
data class WalkTrack(val walk: Walk, val points: List<TrackPoint>)
