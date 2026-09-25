package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.location.LocationPolicy
import dev.catsradar.domain.location.LocationResult
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.region.PlaceCells
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.session.SessionSplitter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Instant

class AttachLocation(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
    private val locationProvider: LocationProvider,
    private val clock: Clock,
) {
    suspend operator fun invoke(encounterId: String) {
        // A retry (process death, WorkManager re-run) must not re-stamp an already-located
        // encounter with a second, different fix, nor repeat its backfill.
        val target = encounterRepository.observeById(encounterId).first()
            ?.takeIf { it.locationSource == LocationSource.NONE }
            ?: return

        val result = resolveLocation()
        val fix = result.fix ?: return

        val geohash = Geohash.encode(fix.lat, fix.lon, Tuning.GEOHASH_PRECISION)
        val placeCellId = PlaceCells.remember(placeCellRepository, geohash)
        val stamp = LocationStamp(
            lat = fix.lat,
            lon = fix.lon,
            accuracyMeters = fix.accuracyMeters,
            locationSource = result.source,
            locationFixedAt = fix.fixedAt,
            geohash = geohash,
            placeCellId = placeCellId,
            updatedAt = clock.now(),
        )

        // The wait above can outlast the target's undo or its location set another way; then this writes nothing.
        val attached = encounterRepository.attachLocation(encounterId, stamp)

        if (attached && result.source == LocationSource.CURRENT_FIX) {
            backfillOuting(target.occurredAt, encounterId, stamp)
        }
    }

    // A backstop, not the primary bound: getCurrentFix already owns LOCATION_TIMEOUT. Double it
    // here so a well-behaved provider's own timeout always resolves first; this only protects the
    // worker if a platform LocationProvider implementation ever stops honouring its own bound -
    // timing out here is equivalent to nothing being available.
    private suspend fun resolveLocation(): LocationResult =
        withTimeoutOrNull(Tuning.LOCATION_TIMEOUT * 2) {
            LocationPolicy.resolve(
                now = clock.now(),
                currentFix = { locationProvider.getCurrentFix(Tuning.LOCATION_TIMEOUT) },
                lastKnown = { locationProvider.lastKnown() },
            )
        } ?: LocationResult(null, LocationSource.NONE)

    private suspend fun backfillOuting(targetOccurredAt: Instant, targetId: String, stamp: LocationStamp) {
        val candidates = encounterRepository.observeAll().first().filter { it.deletedAt == null }
        val outing = SessionSplitter.split(candidates)
            .firstOrNull { targetOccurredAt in it.start..it.end }
            ?: return
        candidates
            // Never overwrites an encounter that already has coordinates.
            .filter { it.id != targetId && it.locationSource == LocationSource.NONE }
            .filter { it.occurredAt in outing.start..outing.end }
            .forEach { encounter ->
                encounterRepository.attachLocation(encounter.id, stamp.copy(locationSource = LocationSource.BACKFILLED))
            }
    }
}
