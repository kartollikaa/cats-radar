package dev.catsradar.domain.model

import kotlin.time.Instant

/** The location columns written together whenever a fix is attached or backfilled. */
data class LocationStamp(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val locationSource: LocationSource,
    val locationFixedAt: Instant,
    val geohash: String,
    val placeCellId: String,
    val updatedAt: Instant,
)
