package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.location.resolveNow
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.region.PlaceCells
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.session.SessionSplitter
import kotlinx.coroutines.flow.first
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

        val result = locationProvider.resolveNow(clock.now())
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

        val attached = encounterRepository.attachLocation(encounterId, stamp)

        if (attached && result.source == LocationSource.CURRENT_FIX) {
            backfillOuting(target.occurredAt, encounterId, stamp)
        }
    }

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
