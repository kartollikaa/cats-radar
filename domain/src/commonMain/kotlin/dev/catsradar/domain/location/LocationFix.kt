package dev.catsradar.domain.location

import kotlin.time.Instant

/** A single location reading: where, how precise, and when it was actually taken. */
data class LocationFix(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val fixedAt: Instant,
)
