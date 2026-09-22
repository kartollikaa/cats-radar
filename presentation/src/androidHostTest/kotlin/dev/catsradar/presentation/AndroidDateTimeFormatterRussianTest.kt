package dev.catsradar.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.datetime.LocalDate
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru")
class AndroidDateTimeFormatterRussianTest {

    private val formatter = AndroidDateTimeFormatter(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun dayHeaderNamesTodayAndYesterdayInTheDeviceLanguage() {
        val today = LocalDate.parse("2026-09-22")

        assertEquals("Сегодня", formatter.dayHeader(date = today, today = today))
        assertEquals("Вчера", formatter.dayHeader(date = LocalDate.parse("2026-09-21"), today = today))
    }

    @Test
    fun durationCarriesItsUnitsInTheDeviceLanguage() {
        assertEquals("20 мин", formatter.duration(TWENTY.minutes))
        assertEquals("1 ч 20 мин", formatter.duration(HOUR_AND_TWENTY.minutes))
    }

    private companion object {
        const val TWENTY = 20
        const val HOUR_AND_TWENTY = 80
    }
}
