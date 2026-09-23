package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.distanceMeters
import dev.catsradar.domain.location.LocationFix
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Adds [fix] to the route of the walk that is on. Returns whether it was kept: a fix is left out
 * when no walk is on, when it is too rough or does not say how rough it is, older than the walk or
 * the route's last point, or too near that point to add anything. Fixes are taken one at a time,
 * each measured against the point the one before it kept.
 */
class RecordTrackPoint(private val walkRepository: WalkRepository) {
    private val turn = Mutex()

    suspend operator fun invoke(fix: LocationFix): Boolean = turn.withLock {
        val walk = walkRepository.openWalk() ?: return false
        val last = walkRepository.lastPoint(walk.id)
        val accuracy = fix.accuracyMeters ?: return@withLock false
        val keep = accuracy <= Tuning.TRACK_MAX_ACCURACY_METERS &&
            fix.fixedAt >= walk.startedAt &&
            (last == null || (fix.fixedAt > last.at && fix.isFarEnoughFrom(last, accuracy)))
        if (keep) {
            walkRepository.appendPoint(TrackPoint(walk.id, fix.fixedAt, fix.lat, fix.lon, accuracy))
        }
        keep
    }

    // A fix wanders by about its accuracy while the phone stands still, so no step is shorter than that.
    private fun LocationFix.isFarEnoughFrom(point: TrackPoint, accuracy: Float): Boolean {
        val step = maxOf(Tuning.TRACK_MIN_STEP_METERS, point.accuracyMeters.toDouble(), accuracy.toDouble())
        return distanceMeters(point.lat, point.lon, lat, lon) >= step
    }
}
