package dev.catsradar.presentation.statistics

import dev.catsradar.domain.stats.Rate
import dev.catsradar.domain.stats.Stats
import dev.catsradar.domain.stats.WalkStats
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.toOption
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.round

private val NothingWalked = WalkStats(walkedMeters = 0.0, catsPerKm = null)

class StatisticsStateMapper(private val dateTimeFormatter: DateTimeFormatter) {

    fun map(stats: Stats, walks: WalkStats = NothingWalked): StatisticsState = StatisticsState(
        total = stats.total,
        hasAnyCats = stats.total > 0,
        todayLabel = stats.today.toString(),
        weekLabel = stats.lastSevenDays.toString(),
        monthLabel = stats.lastThirtyDays.toString(),
        withPhotoLabel = stats.withPhoto.toString(),
        byCoat = stats.byCoat.map { count ->
            CoatShareState(
                coat = count.coat?.toOption(),
                countLabel = count.count.toString(),
                sharePercentLabel = round(count.shareOfTotal * PERCENT).toInt().toString(),
            )
        }.toPersistentList(),
        currentStreakLabel = stats.currentStreak.toString(),
        longestStreakLabel = stats.longestStreak.toString(),
        nextMilestone = stats.nextMilestone?.let {
            MilestoneState(valueLabel = it.value.toString(), remainingLabel = it.remaining.toString())
        },
        outingsLabel = stats.outings.toString(),
        activeTimeLabel = dateTimeFormatter.duration(stats.activeTime),
        overallRate = stats.overallRate?.toRateState(),
        bestOuting = stats.bestOuting?.let {
            BestOutingState(
                count = it.session.count,
                durationLabel = dateTimeFormatter.duration(it.session.duration),
                rate = it.rate.toRateState(),
            )
        },
        walked = walks.walkedMeters.takeIf { it > 0.0 }?.let { meters ->
            WalkedState(distance = meters.toDistanceState(), catsPerKm = walks.catsPerKm?.oneDecimal())
        },
    )
}

/**
 * Cats per hour reads badly once they arrive faster than one a minute ("73 cats/h"), and cats per
 * minute reads badly below that ("0.2 cats/min"); the unit switches where the two meet.
 */
internal fun Rate.toRateState(): RateState = if (perMinute >= 1.0) {
    RateState(value = perMinute.oneDecimal(), unit = RateUnit.PER_MINUTE)
} else {
    RateState(value = perHour.oneDecimal(), unit = RateUnit.PER_HOUR)
}

private const val TENTHS = 10.0
private const val PERCENT = 100
private const val METERS_PER_KM = 1000

private fun Double.oneDecimal(): String {
    val rounded = round(this * TENTHS) / TENTHS
    val whole = rounded.toLong()
    val tenth = round((rounded - whole) * TENTHS).toLong()
    return "$whole.$tenth"
}

private fun Double.toDistanceState(): DistanceState {
    val wholeMeters = round(this).toLong()
    return if (wholeMeters < METERS_PER_KM) {
        DistanceState(value = wholeMeters.toString(), unit = DistanceUnit.METERS)
    } else {
        DistanceState(value = (this / METERS_PER_KM).oneDecimal(), unit = DistanceUnit.KILOMETERS)
    }
}
