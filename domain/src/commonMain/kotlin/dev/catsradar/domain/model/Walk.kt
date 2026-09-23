package dev.catsradar.domain.model

import kotlin.time.Instant

/** A walk the user started and, once [endedAt] is set, ended. Its route is its [TrackPoint]s. */
data class Walk(
    val id: String,
    val startedAt: Instant,
    /** Null while the walk is on. */
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
