package dev.catsradar.presentation.statistics

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class StatisticsState(
    /** The count itself, not a label: only the platform knows the plural form for it. */
    val total: Int = 0,
    val hasAnyCats: Boolean = false,
    val todayLabel: String = "0",
    val weekLabel: String = "0",
    val monthLabel: String = "0",
    val withPhotoLabel: String = "0",
    val byCoat: ImmutableList<CoatShareState> = persistentListOf(),
    val currentStreakLabel: String = "0",
    val longestStreakLabel: String = "0",
    val nextMilestone: MilestoneState? = null,
    val outingsLabel: String = "0",
    val activeTimeLabel: String = "",
    /** Null when no outing was long enough or busy enough to measure. */
    val overallRate: RateState? = null,
    val bestOuting: BestOutingState? = null,
    /** Null when nothing walked has any length. */
    val walked: WalkedState? = null,
)

/** [coat] of null is the "not specified" row. */
data class CoatShareState(val coat: CoatOption?, val countLabel: String, val sharePercentLabel: String)

data class MilestoneState(val valueLabel: String, val remainingLabel: String)

/** [value] already carries its unit; [unit] says which one so the screen can label it. */
data class RateState(val value: String, val unit: RateUnit)

enum class RateUnit { PER_HOUR, PER_MINUTE }

/** [count] is the count itself, not a label: only the platform knows the plural form for it. */
data class BestOutingState(val count: Int, val durationLabel: String, val rate: RateState)

/** [catsPerKm] already carries one decimal; null when no walk was long enough to measure. */
data class WalkedState(val distance: DistanceState, val catsPerKm: String?)

/** [value] is the number alone; [unit] says which unit the screen labels it with. */
data class DistanceState(val value: String, val unit: DistanceUnit)

enum class DistanceUnit { METERS, KILOMETERS }
