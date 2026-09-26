package dev.catsradar.app.widget

import dev.catsradar.domain.usecase.ObserveTodayCount
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

private val Now = Instant.parse("2026-09-22T12:00:00Z")

class WidgetRefreshTest {

    private val encounters = FakeTodayRepository()
    private var redraws = 0

    private val count = WidgetCount(
        ObserveTodayCount(
            encounterRepository = encounters,
            clock = object : Clock {
                override fun now(): Instant = Now
            },
        ) { TimeZone.UTC },
    )

    private fun TestScope.startRefresh() {
        count.start(backgroundScope)
        WidgetRefresh(widgetCount = count, widgetRedraw = { redraws++ }).start(backgroundScope)
    }

    // A process can start after midnight, or because a lock-screen tap just wrote a row: in both the
    // widget is showing an older number than the first one read here.
    @Test
    fun startingRedrawsOnceWithWhatIsTrueNow() = runTest(UnconfinedTestDispatcher()) {
        encounters.add(id = "a", at = Now)
        startRefresh()

        assertEquals(1, redraws)
    }

    @Test
    fun aCatLoggedElsewhereRedrawsTheWidget() = runTest(UnconfinedTestDispatcher()) {
        startRefresh()

        encounters.add(id = "a", at = Now)

        assertEquals(2, redraws)
    }

    @Test
    fun aCatUndoneElsewhereRedrawsTheWidget() = runTest(UnconfinedTestDispatcher()) {
        encounters.add(id = "a", at = Now)
        startRefresh()

        encounters.softDelete("a", deletedAt = Now)

        assertEquals(2, redraws)
    }

    @Test
    fun aCatOnAnotherDayLeavesTheWidgetAlone() = runTest(UnconfinedTestDispatcher()) {
        startRefresh()

        encounters.add(id = "old", at = Instant.parse("2026-09-20T12:00:00Z"))

        assertEquals(1, redraws)
    }

    @Test
    fun aTapRedrawsTheWidgetBeforeItsRowIsWritten() = runTest(UnconfinedTestDispatcher()) {
        startRefresh()

        count.tally {
            assertEquals(2, redraws)
            encounters.add(id = "a", at = Now)
        }

        assertEquals(2, redraws)
    }
}
