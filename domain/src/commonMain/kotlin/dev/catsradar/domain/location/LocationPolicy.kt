package dev.catsradar.domain.location

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.LocationSource
import kotlin.time.Duration
import kotlin.time.Instant

data class LocationResult(val fix: LocationFix?, val source: LocationSource)

/**
 * Resolves the best available location: a fresh fix first, a recent last-known fix otherwise,
 * nothing when neither is usable. No permission handling and no platform I/O of its own —
 * [currentFix] and [lastKnown] already encapsulate that. [lastKnown] is only invoked when
 * [currentFix] comes back empty, so a successful fix never pays for the extra round trip.
 */
object LocationPolicy {
    suspend fun resolve(
        now: Instant,
        currentFix: suspend () -> LocationFix?,
        lastKnown: suspend () -> LocationFix?,
        lastKnownMaxAge: Duration = Tuning.LAST_KNOWN_MAX_AGE,
    ): LocationResult {
        val fix = currentFix()
        return if (fix != null) {
            LocationResult(fix, LocationSource.CURRENT_FIX)
        } else {
            val recent = lastKnown()
            val lastKnownIsFresh = recent != null && now - recent.fixedAt <= lastKnownMaxAge
            if (lastKnownIsFresh) {
                LocationResult(recent, LocationSource.LAST_KNOWN)
            } else {
                LocationResult(null, LocationSource.NONE)
            }
        }
    }
}
