package dev.catsradar.domain.usecase

import dev.catsradar.domain.platform.LocationProvider

/** Offers every fix the device reports to the route of the walk that is on, until cancelled. */
class RecordWalk(
    private val locationProvider: LocationProvider,
    private val recordTrackPoint: RecordTrackPoint,
) {
    suspend operator fun invoke() {
        locationProvider.trackFixes().collect { recordTrackPoint(it) }
    }
}
