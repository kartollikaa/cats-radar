package dev.catsradar.presentation

import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlin.time.Duration
import kotlin.time.Instant

/** Locale-aware formatting for mappers; the Android implementation lives in `androidMain`. */
interface DateTimeFormatter {
    /** [date] named relative to [today] when the two are adjacent, otherwise as a calendar date. */
    fun dayHeader(date: LocalDate, today: LocalDate): String

    /** [instant] as a localized wall-clock time at [offset]. */
    fun time(instant: Instant, offset: UtcOffset): String

    /** [duration] as a short span; the hour part is left out below one hour. */
    fun duration(duration: Duration): String
}
