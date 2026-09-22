package dev.catsradar.domain.stats

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Runs of consecutive calendar days on which at least one cat was logged. */
internal object Streaks {

    /** Yesterday still counts: a streak should not break at midnight, before the user is out. */
    fun current(days: Set<LocalDate>, today: LocalDate): Int {
        val end = listOf(today, today.minusDay()).firstOrNull { it in days } ?: return 0
        return runEndingAt(days, end)
    }

    fun longest(days: Set<LocalDate>): Int =
        days.filter { it.minusDay() !in days }
            .maxOfOrNull { start -> runStartingAt(days, start) }
            ?: 0

    private fun runEndingAt(days: Set<LocalDate>, end: LocalDate): Int {
        var length = 0
        var day = end
        while (day in days) {
            length++
            day = day.minusDay()
        }
        return length
    }

    private fun runStartingAt(days: Set<LocalDate>, start: LocalDate): Int {
        var length = 0
        var day = start
        while (day in days) {
            length++
            day = day.plusDay()
        }
        return length
    }

    private fun LocalDate.minusDay(): LocalDate = minus(DatePeriod(days = 1))

    private fun LocalDate.plusDay(): LocalDate = plus(DatePeriod(days = 1))
}
