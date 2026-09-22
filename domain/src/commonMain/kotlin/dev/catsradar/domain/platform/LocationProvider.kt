package dev.catsradar.domain.platform

import dev.catsradar.domain.location.LocationFix
import kotlin.time.Duration

/**
 * The device's Fused location. An implementation must not throw when location permission is
 * absent — it returns null so [dev.catsradar.domain.location.LocationPolicy] falls through to the
 * next rung.
 */
interface LocationProvider {
    suspend fun getCurrentFix(timeout: Duration): LocationFix?
    suspend fun lastKnown(): LocationFix?
}
