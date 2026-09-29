package dev.catsradar.presentation.statistics

sealed interface StatisticsIntent {
    data class RangePicked(val range: ChartRange) : StatisticsIntent

    data class DayPicked(val epochDay: Long) : StatisticsIntent
}
