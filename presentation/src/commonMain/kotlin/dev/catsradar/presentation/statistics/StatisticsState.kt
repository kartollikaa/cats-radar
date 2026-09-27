package dev.catsradar.presentation.statistics

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class StatisticsState(
    /** The count itself, not a label: only the platform knows the plural form for it. */
    val total: Int = 0,
    val hasAnyCats: Boolean = false,
    val chart: DayChartState = DayChartState(),
    val todayLabel: String = "0",
    val weekLabel: String = "0",
    val monthLabel: String = "0",
    val withPhotoLabel: String = "0",
    val byCoat: ImmutableList<CoatShareState> = persistentListOf(),
    /** The streaks as counts, not labels: only the platform knows the plural form of their days. */
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val nextMilestone: MilestoneState? = null,
    val outingsLabel: String = "0",
    val activeTimeLabel: String = "",
    /** Null when no outing was long enough or busy enough to measure. */
    val overallRate: RateState? = null,
    val bestOuting: BestOutingState? = null,
    /** Null when nothing walked has any length. */
    val walked: WalkedState? = null,
)

/** [coat] of null is the "not specified" row; [share] is its count over the busiest row's, for its bar. */
data class CoatShareState(
    val coat: CoatOption?,
    val countLabel: String,
    val sharePercentLabel: String,
    val share: Float,
)

enum class ChartRange(val days: Int) { WEEK(days = 7), MONTH(days = 30) }

/** The cats of each day of [range], oldest first and today last, and the day the line under them names. */
data class DayChartState(
    val range: ChartRange = ChartRange.WEEK,
    val bars: ImmutableList<DayBarState> = persistentListOf(),
    val picked: PickedDayState? = null,
)

/**
 * One day's bar. [epochDay] is the key a tap reports; [height] is the day's cats over the busiest day's,
 * 0 with none; [axisLabel] is null for a bar drawn without one.
 */
data class DayBarState(
    val epochDay: Long,
    val count: Int,
    val height: Float,
    val isToday: Boolean,
    val isPicked: Boolean,
    val axisLabel: String?,
    val dayLabel: String,
)

data class PickedDayState(val count: Int, val dayLabel: String)

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
