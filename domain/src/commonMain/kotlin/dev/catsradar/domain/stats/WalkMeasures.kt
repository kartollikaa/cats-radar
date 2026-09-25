package dev.catsradar.domain.stats

import dev.catsradar.domain.geo.trackLengthMeters
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.model.WalkTrack

/** A walk's route length in metres, taken over [pointCount] recorded points, and how many cats it covers. */
data class MeasuredWalk(val walk: Walk, val pointCount: Int, val meters: Double, val cats: Int)

/** Every walk measured among [cats]. */
data class WalkMeasures(val cats: CatTimes, val walks: List<MeasuredWalk>) {

    /**
     * [tracks] measured among [cats], keeping this measure's length for a route that has not grown, and
     * its cat count for a walk whose span has not changed when [cats] equal the ones counted before.
     */
    fun next(cats: CatTimes, tracks: List<WalkTrack>): WalkMeasures {
        val before = walks.associateBy { it.walk.id }
        val sameCats = cats == this.cats
        val measured = tracks.map { track ->
            val walk = track.walk
            val known = before[walk.id]
            MeasuredWalk(
                walk = walk,
                pointCount = track.points.size,
                // Routes only ever gain points, so an unchanged count is an unchanged route.
                meters = known?.takeIf { it.pointCount == track.points.size }?.meters
                    ?: trackLengthMeters(track.points),
                cats = known?.takeIf { sameCats && it.walk.spansAs(walk) }?.cats
                    ?: cats.countWithin(walk.startedAt, walk.endedAt),
            )
        }
        return WalkMeasures(cats, measured)
    }

    private fun Walk.spansAs(other: Walk): Boolean = startedAt == other.startedAt && endedAt == other.endedAt

    companion object {
        val NONE = WalkMeasures(CatTimes.NONE, emptyList())
    }
}
