package dev.catsradar.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS")
class AndroidDateTimeFormatterShortDatesTest {

    private val formatter = AndroidDateTimeFormatter(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun shortDatesNameTheWeekdayTheDayAndTheMonthWithoutTheYear() {
        val date = LocalDate.parse("2026-09-26")

        assertEquals("Sat", formatter.weekday(date))
        assertEquals("Sep 26", formatter.dayMonth(date))
        assertEquals("Sat, Sep 26", formatter.weekdayDayMonth(date))
    }

    @Test
    fun shortDatesFollowTheDeviceLanguageWhenItChanges() {
        val date = LocalDate.parse("2026-09-26")
        val before = Locale.getDefault()
        formatter.weekdayDayMonth(date)

        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            assertEquals("сб, 26 сент.", formatter.weekdayDayMonth(date))
        } finally {
            Locale.setDefault(before)
        }
    }
}
