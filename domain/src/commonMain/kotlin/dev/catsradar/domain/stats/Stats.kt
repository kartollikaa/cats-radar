package dev.catsradar.domain.stats

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Session
import kotlin.time.Duration

data class Stats(
    val total: Int,
    val today: Int,
    val lastSevenDays: Int,
    val lastThirtyDays: Int,
    val withPhoto: Int,
    /** Busiest coat first; "not specified" last. Coats nobody has seen are absent. */
    val byCoat: List<CoatCount>,
    val currentStreak: Int,
    val longestStreak: Int,
    val nextMilestone: Milestone?,
    val outings: Int,
    val activeTime: Duration,
    /** Null when no outing was long enough or busy enough to measure — see [Rate]. */
    val overallRate: Rate?,
    val bestOuting: RatedOuting?,
    val currentOuting: CurrentOuting?,
)

/** [coat] of null is the "not specified" row. */
data class CoatCount(val coat: CatCoat?, val count: Int, val shareOfTotal: Double)

/** [remaining] is how many more cats reach [value]; never zero, because a reached milestone is past. */
data class Milestone(val value: Int, val remaining: Int)

/**
 * A speed of encounters, kept as cats per hour because that is the unit that stays readable at the
 * rates a walk actually produces. Presentation decides whether to show hours or minutes.
 */
@JvmInline
value class Rate(val perHour: Double) {
    val perMinute: Double get() = perHour / MINUTES_PER_HOUR

    private companion object {
        const val MINUTES_PER_HOUR = 60.0
    }
}

data class RatedOuting(val session: Session, val rate: Rate)

/** The outing in progress: the last cat was recent enough that the next one would join it. */
data class CurrentOuting(val count: Int, val elapsed: Duration, val rate: Rate?)
