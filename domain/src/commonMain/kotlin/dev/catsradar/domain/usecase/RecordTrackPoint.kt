package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.distanceMeters
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.repository.WalkRepository

/**
 * Adds [fix] to the route of the walk that is on. Returns whether it was kept: a fix is left out
 * when no walk is on, when it is too rough, older than the walk or the route's last point, or too
 * near that point to add anything. Calls must not overlap, or two fixes can both be measured against
 * the same last point.
 */
class RecordTrackPoint(private val walkRepository: WalkRepository) {
    suspend operator fun invoke(fix: LocationFix): Boolean {
        val walk = walkRepository.openWalk() ?: return false
        val last = walkRepository.lastPoint(walk.id)
        val keep = fix.accuracyMeters <= Tuning.TRACK_MAX_ACCURACY_METERS &&
            fix.fixedAt >= walk.startedAt &&
            (last == null || (fix.fixedAt > last.at && fix.isFarEnoughFrom(last)))
        if (keep) {
            walkRepository.appendPoint(
                TrackPoint(walk.id, fix.fixedAt, fix.lat, fix.lon, fix.accuracyMeters),
            )
        }
        return keep
    }

    private fun LocationFix.isFarEnoughFrom(point: TrackPoint): Boolean =
        distanceMeters(point.lat, point.lon, lat, lon) >= Tuning.TRACK_MIN_STEP_METERS
}
