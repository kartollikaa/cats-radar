package dev.catsradar.domain.location

import kotlin.time.Instant

/** A single location reading: where, how precise, and when it was actually taken. */
data class LocationFix(
    val lat: Double,
    val lon: Double,
    /** Null when the reading says nothing about how precise it is. */
    val accuracyMeters: Float?,
    val fixedAt: Instant,
)
