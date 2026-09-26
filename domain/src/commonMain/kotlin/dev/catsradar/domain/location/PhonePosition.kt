package dev.catsradar.domain.location

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.LocationProvider
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Instant

// A backstop, not the primary bound: getCurrentFix already owns LOCATION_TIMEOUT. Doubled so a well-behaved
// provider's own timeout always resolves first; timing out here is the same as nothing being available.
internal suspend fun LocationProvider.resolveNow(now: Instant): LocationResult =
    withTimeoutOrNull(Tuning.LOCATION_TIMEOUT * 2) {
        LocationPolicy.resolve(
            now = now,
            currentFix = { getCurrentFix(Tuning.LOCATION_TIMEOUT) },
            lastKnown = { lastKnown() },
        )
    } ?: LocationResult(null, LocationSource.NONE)
