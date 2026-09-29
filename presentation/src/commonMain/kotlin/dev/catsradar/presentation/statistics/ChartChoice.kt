package dev.catsradar.presentation.statistics

/** What the day chart shows: its range, and the day picked by its epoch day, or null for today. */
data class ChartChoice(val range: ChartRange = ChartRange.WEEK, val pickedDay: Long? = null)
