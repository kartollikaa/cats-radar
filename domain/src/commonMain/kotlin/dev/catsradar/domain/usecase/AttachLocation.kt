package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.location.LocationPolicy
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.session.SessionSplitter
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant

class AttachLocation(
    private val encounterRepository: EncounterRepository,
    private val locationProvider: LocationProvider,
    private val clock: Clock,
) {
    suspend operator fun invoke(encounterId: String) {
        val target = encounterRepository.observeById(encounterId).first() ?: return
        val outingCandidates = encounterRepository.observeAll().first().filter { it.deletedAt == null }

        val result = LocationPolicy.resolve(
            now = clock.now(),
            currentFix = { locationProvider.getCurrentFix(Tuning.LOCATION_TIMEOUT) },
            lastKnown = locationProvider.lastKnown(),
        )
        val fix = result.fix ?: return
        val geohash = Geohash.encode(fix.lat, fix.lon, Tuning.GEOHASH_PRECISION)
        val placeCellId = Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION)
        val fixed = Fixed(fix, geohash, placeCellId, clock.now())

        encounterRepository.update(target.locatedAt(fixed, result.source))

        if (result.source == LocationSource.CURRENT_FIX) {
            backfillOuting(target, outingCandidates, fixed)
        }
    }

    private suspend fun backfillOuting(target: Encounter, candidates: List<Encounter>, fixed: Fixed) {
        val outing = SessionSplitter.split(candidates).firstOrNull { target.occurredAt in it.start..it.end } ?: return
        candidates
            // Never overwrites an encounter that already has coordinates.
            .filter { it.id != target.id && it.locationSource == LocationSource.NONE }
            .filter { it.occurredAt in outing.start..outing.end }
            .forEach { encounter ->
                encounterRepository.update(encounter.locatedAt(fixed, LocationSource.BACKFILLED))
            }
    }

    private fun Encounter.locatedAt(fixed: Fixed, source: LocationSource): Encounter = copy(
        lat = fixed.fix.lat,
        lon = fixed.fix.lon,
        accuracyMeters = fixed.fix.accuracyMeters,
        locationSource = source,
        locationFixedAt = fixed.fix.fixedAt,
        geohash = fixed.geohash,
        placeCellId = fixed.placeCellId,
        updatedAt = fixed.updatedAt,
    )

    private data class Fixed(
        val fix: LocationFix,
        val geohash: String,
        val placeCellId: String,
        val updatedAt: Instant,
    )
}
