package dev.catsradar.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class AndroidDateTimeFormatterTest {

    private val formatter = AndroidDateTimeFormatter(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun dayHeaderLabelsTheSameDateAsToday() {
        val today = LocalDate.parse("2026-09-22")

        assertEquals("Today", formatter.dayHeader(date = today, today = today))
    }

    @Test
    fun dayHeaderLabelsTheDayBeforeTodayAsYesterday() {
        val today = LocalDate.parse("2026-09-22")

        assertEquals("Yesterday", formatter.dayHeader(date = LocalDate.parse("2026-09-21"), today = today))
    }

    @Test
    fun dayHeaderCrossesAYearBoundaryForYesterdayOnJanuaryFirst() {
        val today = LocalDate.parse("2026-01-01")

        assertEquals("Yesterday", formatter.dayHeader(date = LocalDate.parse("2025-12-31"), today = today))
    }

    @Test
    fun dayHeaderCrossesAMonthBoundaryForYesterday() {
        val today = LocalDate.parse("2026-03-01")

        assertEquals("Yesterday", formatter.dayHeader(date = LocalDate.parse("2026-02-28"), today = today))
    }

    @Test
    fun dayHeaderFormatsAnOlderDateAsACalendarDateNotTodayOrYesterday() {
        val today = LocalDate.parse("2026-09-22")

        val label = formatter.dayHeader(date = LocalDate.parse("2026-09-10"), today = today)

        assertEquals("Sep 10, 2026", label)
    }

    @Test
    fun durationUnderAnHourLeavesOutTheHourPart() {
        assertEquals("45 min", formatter.duration(MINUTES_UNDER_AN_HOUR.minutes))
    }

    @Test
    fun durationOverAnHourCarriesBothParts() {
        assertEquals("1 h 20 min", formatter.duration(MINUTES_OVER_AN_HOUR.minutes))
    }

    @Test
    fun timeRendersTheLocalHourAndMinuteAPositiveOffsetProduces() {
        // 2026-09-22T01:15:00Z at UTC+02:00 is 2026-09-22T03:15 local.
        val label = formatter.time(Instant.parse("2026-09-22T01:15:00Z"), UtcOffset(minutes = POSITIVE_OFFSET_MINUTES))

        assertTrue(label.contains("3:15"), "expected a 3:15 time label, was \"$label\"")
        assertTrue(label.contains("AM"), "expected an AM time label, was \"$label\"")
    }

    @Test
    fun timeIsSensitiveToANegativeUtcOffsetNotTheInstantAlone() {
        val instant = Instant.parse("2026-09-22T01:00:00Z")

        val atUtc = formatter.time(instant, UtcOffset(minutes = 0))
        // UTC-3: the same instant falls on the previous local day, at 22:00.
        val atNegativeOffset = formatter.time(instant, UtcOffset(minutes = NEGATIVE_OFFSET_MINUTES))

        assertNotEquals(atUtc, atNegativeOffset)
        assertTrue(atUtc.contains("1:00") && atUtc.contains("AM"), "expected a 1:00 AM label, was \"$atUtc\"")
        assertTrue(
            atNegativeOffset.contains("10:00") && atNegativeOffset.contains("PM"),
            "expected a 10:00 PM label, was \"$atNegativeOffset\"",
        )
    }

    private companion object {
        const val POSITIVE_OFFSET_MINUTES = 120
        const val NEGATIVE_OFFSET_MINUTES = -180
        const val MINUTES_UNDER_AN_HOUR = 45
        const val MINUTES_OVER_AN_HOUR = 80
    }
}
