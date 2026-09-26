package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.geo.isOnGlobe
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.region.PlaceCells
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

class SetLocationByHand(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    /** True when the cat now has this point; false when it is gone, already located, or the point is off the globe. */
    suspend operator fun invoke(encounterId: String, lat: Double, lon: Double): Boolean {
        if (!isOnGlobe(lat, lon) || !isUnlocated(encounterId)) return false

        val geohash = Geohash.encode(lat, lon, Tuning.GEOHASH_PRECISION)
        val now = clock.now()
        val stamp = LocationStamp(
            lat = lat,
            lon = lon,
            accuracyMeters = null,
            locationSource = LocationSource.MANUAL,
            locationFixedAt = now,
            geohash = geohash,
            placeCellId = PlaceCells.remember(placeCellRepository, geohash),
            updatedAt = now,
        )
        val set = encounterRepository.attachLocation(encounterId, stamp)
        if (set) analytics.log(AnalyticsEvent.LocationSetByHand)
        return set
    }

    private suspend fun isUnlocated(encounterId: String): Boolean =
        encounterRepository.observeById(encounterId).first()
            ?.let { it.deletedAt == null && it.locationSource == LocationSource.NONE } == true
}
