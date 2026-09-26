package dev.catsradar.domain.usecase

import dev.catsradar.domain.geo.GeoPoint
import dev.catsradar.domain.geo.pointOnGlobe
import dev.catsradar.domain.location.closestLocatedInTime
import dev.catsradar.domain.model.locatedPoint
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.first

class WhereToLook(
    private val encounterRepository: EncounterRepository,
    private val locationProvider: LocationProvider,
) {
    /** Where a cat was most likely seen: the located cat closest in time, else the phone's last position. */
    suspend operator fun invoke(encounterId: String): GeoPoint? {
        val encounters = encounterRepository.observeAll().first()
        val closest = encounters.firstOrNull { it.id == encounterId }
            ?.let { encounters.closestLocatedInTime(it) }
            ?.locatedPoint()
        return closest ?: locationProvider.lastKnown()?.let { pointOnGlobe(it.lat, it.lon) }
    }
}
