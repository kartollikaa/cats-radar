package dev.catsradar.domain.stats

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.WalkTrack

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
    ): WalkStats = calculate(WalkMeasures.NONE.next(CatTimes.of(encounters), walks).walks, minRateDistanceMeters)

    // Pooled like the overall rate: one short lucky walk must not outweigh a long ordinary one.
    fun calculate(
        walks: List<MeasuredWalk>,
        minRateDistanceMeters: Double = Tuning.MIN_RATE_DISTANCE_METERS,
    ): WalkStats {
        val measured = walks.filter { it.meters >= minRateDistanceMeters }
        val catsPerKm = if (measured.isEmpty()) {
            null
        } else {
            measured.sumOf { it.cats } / (measured.sumOf { it.meters } / METERS_PER_KM)
        }
        return WalkStats(walkedMeters = walks.sumOf { it.meters }, catsPerKm = catsPerKm)
    }

    private const val METERS_PER_KM = 1000.0
}
