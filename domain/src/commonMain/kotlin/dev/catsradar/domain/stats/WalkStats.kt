package dev.catsradar.domain.stats

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.trackLengthMeters
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.WalkTrack
import dev.catsradar.domain.walk.covers

data class WalkStats(
    /** The length of every walk's recorded route, in metres. */
    val walkedMeters: Double,
    /** Null when no walk was long enough to measure — see [Tuning.MIN_RATE_DISTANCE_METERS]. */
    val catsPerKm: Double?,
)

object WalkStatsCalculator {

    fun calculate(
        encounters: List<Encounter>,
        walks: List<WalkTrack>,
        minRateDistanceMeters: Double = Tuning.MIN_RATE_DISTANCE_METERS,
    ): WalkStats {
        val live = encounters.filter { it.deletedAt == null }
        return WalkStats(
            walkedMeters = walks.sumOf { trackLengthMeters(it.points) },
            catsPerKm = catsPerKm(live, walks, minRateDistanceMeters),
        )
    }

    // Pooled like the overall rate: one short lucky walk must not outweigh a long ordinary one.
    private fun catsPerKm(live: List<Encounter>, walks: List<WalkTrack>, minDistanceMeters: Double): Double? {
        val measured = walks
            .map { it to trackLengthMeters(it.points) }
            .filter { (_, meters) -> meters >= minDistanceMeters }
        if (measured.isEmpty()) return null
        val cats = measured.sumOf { (track, _) -> live.count { track.walk.covers(it.occurredAt) } }
        val kilometres = measured.sumOf { (_, meters) -> meters } / METERS_PER_KM
        return cats / kilometres
    }

    private const val METERS_PER_KM = 1000.0
}
