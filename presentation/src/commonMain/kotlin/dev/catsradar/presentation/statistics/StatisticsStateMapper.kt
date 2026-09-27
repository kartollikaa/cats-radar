package dev.catsradar.presentation.statistics

import dev.catsradar.domain.stats.DayCount
import dev.catsradar.domain.stats.Rate
import dev.catsradar.domain.stats.Stats
import dev.catsradar.domain.stats.WalkStats
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.toOption
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate
import kotlin.math.round

private val NothingWalked = WalkStats(walkedMeters = 0.0, catsPerKm = null)

class StatisticsStateMapper(private val dateTimeFormatter: DateTimeFormatter) {

    fun map(stats: Stats, walks: WalkStats = NothingWalked, chart: ChartChoice = ChartChoice()): StatisticsState {
        val busiestCoat = stats.byCoat.maxOfOrNull { it.count } ?: 0
        return StatisticsState(
            total = stats.total,
            hasAnyCats = stats.total > 0,
            chart = dayChart(stats.byDay, chart),
            todayLabel = stats.today.toString(),
            weekLabel = stats.lastSevenDays.toString(),
            monthLabel = stats.lastThirtyDays.toString(),
            withPhotoLabel = stats.withPhoto.toString(),
            byCoat = stats.byCoat.map { count ->
                CoatShareState(
                    coat = count.coat?.toOption(),
                    countLabel = count.count.toString(),
                    sharePercentLabel = round(count.shareOfTotal * PERCENT).toInt().toString(),
                    share = count.count.toFloat() / busiestCoat,
                )
            }.toPersistentList(),
            currentStreak = stats.currentStreak,
            longestStreak = stats.longestStreak,
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

    private fun dayChart(byDay: List<DayCount>, choice: ChartChoice): DayChartState {
        val shown = byDay.takeLast(choice.range.days)
        val busiest = shown.maxOfOrNull { it.count } ?: 0
        val picked = shown.firstOrNull { it.date.toEpochDays() == choice.pickedDay } ?: shown.lastOrNull()
        val bars = shown.mapIndexed { index, day ->
            val daysBack = shown.lastIndex - index
            DayBarState(
                epochDay = day.date.toEpochDays(),
                count = day.count,
                height = if (busiest > 0) day.count.toFloat() / busiest else 0f,
                isToday = daysBack == 0,
                isPicked = day.date == picked?.date,
                axisLabel = axisLabel(day.date, daysBack, choice.range),
                dayLabel = dateTimeFormatter.weekdayDayMonth(day.date),
            )
        }
        return DayChartState(
            range = choice.range,
            bars = bars.toPersistentList(),
            picked = bars.firstOrNull { it.isPicked }?.let { PickedDayState(count = it.count, dayLabel = it.dayLabel) },
        )
    }

    // Thirty labels cannot fit under thirty bars: a month is dated once a week, counting back from today.
    private fun axisLabel(date: LocalDate, daysBack: Int, range: ChartRange): String? = when (range) {
        ChartRange.WEEK -> dateTimeFormatter.weekday(date)
        ChartRange.MONTH -> dateTimeFormatter.dayMonth(date).takeIf { daysBack % WEEK_DAYS == 0 }
    }
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

private const val WEEK_DAYS = 7
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
