package dev.catsradar.domain.usecase

import dev.catsradar.domain.geo.GeoPoint
import dev.catsradar.domain.geo.pointOnGlobe
import dev.catsradar.domain.location.resolveNow
import dev.catsradar.domain.platform.LocationProvider
import kotlin.time.Clock

class LocatePhone(private val locationProvider: LocationProvider, private val clock: Clock) {
    /** Where the phone is, found the way a tally finds it; null when it cannot say. */
    suspend operator fun invoke(): GeoPoint? =
        locationProvider.resolveNow(clock.now()).fix?.let { pointOnGlobe(it.lat, it.lon) }
}
