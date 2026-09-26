package dev.catsradar.app.widget

import dev.catsradar.domain.usecase.ObserveTodayCount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant

private val Morning = Instant.parse("2026-09-22T09:00:00Z")
private val BeforeMidnight = Instant.parse("2026-09-22T23:59:00Z")
private val AfterMidnight = Instant.parse("2026-09-23T00:01:00Z")

// Storage answers on the test's own scheduler, so it only catches up when the test lets it run.
class WidgetCountTest {

    private val encounters = FakeTodayRepository()
    private var now = Morning
    private val count = WidgetCount(
        ObserveTodayCount(
            encounterRepository = encounters,
            clock = object : Clock {
                override fun now(): Instant = now
            },
        ) { TimeZone.UTC },
    )

    /** Every number the widget is given, in order. */
    private fun TestScope.started(): List<Int> {
        count.start(backgroundScope)
        val shown = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { count.shown.toList(shown) }
        runCurrent()
        return shown
    }

    @Test
    fun aTapShowsBeforeItsRowIsWritten() = runTest {
        encounters.add(id = "a", at = Morning)
        started()

        count.tally {
            assertEquals(2, count.shown.first())
            encounters.add(id = "b", at = Morning)
        }
    }

    @Test
    fun theTapHoldsUntilTheStoredCountCatchesUp() = runTest {
        encounters.add(id = "a", at = Morning)
        val shown = started()

        count.tally { encounters.add(id = "b", at = Morning) }
        runCurrent()

        assertEquals(listOf(1, 2), shown)
    }

    @Test
    fun aRowStoredBeforeTheWriteReturnsIsCountedOnce() = runTest {
        encounters.add(id = "a", at = Morning)
        val shown = started()

        count.tally {
            encounters.add(id = "b", at = Morning)
            runCurrent()
        }
        runCurrent()

        assertEquals(listOf(1, 2), shown)
    }

    @Test
    fun onceCaughtUpTheStoredCountTakesOver() = runTest {
        encounters.add(id = "a", at = Morning)
        val shown = started()
        count.tally { encounters.add(id = "b", at = Morning) }
        runCurrent()

        encounters.softDelete("b", deletedAt = Morning)
        runCurrent()

        assertEquals(listOf(1, 2, 1), shown)
    }

    @Test
    fun aFailedWriteTakesTheTapBackOff() = runTest {
        encounters.add(id = "a", at = Morning)
        val shown = started()

        assertFailsWith<IllegalStateException> { count.tally { error("disk full") } }
        runCurrent()

        assertEquals(listOf(1, 2, 1), shown)
    }

    @Test
    fun aTapSettlesEvenWhenTheCountCannotBeReadBack() = runTest {
        encounters.add(id = "a", at = Morning)
        val shown = started()

        assertFailsWith<IllegalStateException> {
            count.tally {
                encounters.failNewReads = true
                error("disk full")
            }
        }
        runCurrent()

        assertEquals(listOf(1, 2, 1), shown)
    }

    @Test
    fun tapsInFlightTogetherEachCount() = runTest {
        encounters.add(id = "a", at = Morning)
        val shown = started()
        val firstWrite = CompletableDeferred<Unit>()
        val secondWrite = CompletableDeferred<Unit>()
        launch {
            count.tally {
                firstWrite.await()
                encounters.add(id = "b", at = Morning)
            }
        }
        launch {
            count.tally {
                secondWrite.await()
                encounters.add(id = "c", at = Morning)
            }
        }
        runCurrent()

        firstWrite.complete(Unit)
        runCurrent()
        secondWrite.complete(Unit)
        runCurrent()

        assertEquals(listOf(1, 2, 3), shown)
    }

    @Test
    fun aTapAfterMidnightEndsOnTheNewDaysCount() = runTest {
        now = BeforeMidnight
        encounters.add(id = "a", at = BeforeMidnight)
        encounters.add(id = "b", at = BeforeMidnight)
        val shown = started()
        now = AfterMidnight

        count.tally { encounters.add(id = "c", at = AfterMidnight) }
        runCurrent()

        assertEquals(1, shown.last())
    }

    @Test
    fun aRefreshAfterMidnightReadsTheNewDay() = runTest {
        now = BeforeMidnight
        encounters.add(id = "a", at = BeforeMidnight)
        val shown = started()
        now = AfterMidnight

        count.refresh()

        assertEquals(listOf(1, 0), shown)
    }

    @Test
    fun aStaleRefreshIsIgnored() = runTest {
        encounters.add(id = "a", at = Morning)
        val shown = started()
        val release = encounters.holdNextRead()
        launch { count.refresh() }
        runCurrent()

        encounters.add(id = "b", at = Morning)
        runCurrent()
        release.complete(Unit)
        runCurrent()

        assertEquals(listOf(1, 2), shown)
    }
}
