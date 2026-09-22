package dev.catsradar.presentation

import android.content.Context
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Instant
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter

class AndroidDateTimeFormatter(private val context: Context) : DateTimeFormatter {

    override fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
        today -> context.getString(R.string.day_today)
        today.minus(1, DateTimeUnit.DAY) -> context.getString(R.string.day_yesterday)
        else -> JavaDateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(date.toJavaLocalDate())
    }

    override fun time(instant: Instant, offset: UtcOffset): String =
        JavaDateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(instant.toLocalDateTime(offset.asTimeZone()).toJavaLocalDateTime())

    override fun duration(duration: Duration): String = duration.toComponents { hours, minutes, _, _ ->
        if (hours > 0) {
            context.getString(R.string.duration_hours_minutes, hours, minutes)
        } else {
            context.getString(R.string.duration_minutes, minutes)
        }
    }
}
