package dev.catsradar.presentation

import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlin.time.Duration
import kotlin.time.Instant

/** Locale-aware formatting for mappers; the Android implementation lives in `androidMain`. */
interface DateTimeFormatter {
    /** [date] relative to [today]: "Today", "Yesterday", or a localized calendar date. */
    fun dayHeader(date: LocalDate, today: LocalDate): String

    /** [instant] as a localized wall-clock time at [offset]. */
    fun time(instant: Instant, offset: UtcOffset): String

    /** [duration] as a short localized span, e.g. "1 h 20 min". */
    fun duration(duration: Duration): String
}
