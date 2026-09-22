package dev.catsradar.domain.usecase

import app.cash.turbine.test
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.encounterAt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

private val Noon = Instant.parse("2026-09-22T12:00:00Z")

private class MovableClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

class ObserveTodayCountTest {

    private val repository = FakeEncounterRepository()

    private fun countIn(zone: TimeZone, now: Instant = Noon) =
        ObserveTodayCount(repository, FakeClock(now)) { zone }

    @Test
    fun `counts only the cats whose own day is today`() = runTest {
        repository.insert(encounterAt(Noon).copy(id = "today"))
        repository.insert(encounterAt(Instant.parse("2026-09-21T12:00:00Z")).copy(id = "yesterday"))
        repository.insert(encounterAt(Instant.parse("2026-09-23T12:00:00Z")).copy(id = "tomorrow"))

        assertEquals(1, countIn(TimeZone.UTC)().first())
    }

    @Test
    fun `a cat deleted after it was counted is taken off the count`() = runTest {
        repository.insert(encounterAt(Noon).copy(id = "kept"))
        repository.insert(encounterAt(Noon).copy(id = "removed"))
        countIn(TimeZone.UTC)().test {
            assertEquals(2, awaitItem())

            repository.softDelete("removed", deletedAt = Noon)

            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Yesterday's 1 and today's 1 are different answers; collapsing them would leave a widget that
    // redrew at midnight showing 0 after the morning's first cat.
    @Test
    fun `the same count on a new day is still news`() = runTest {
        val clock = MovableClock(Noon)
        repository.insert(encounterAt(Noon).copy(id = "yesterday"))
        ObserveTodayCount(repository, clock) { TimeZone.UTC }().test {
            assertEquals(1, awaitItem())

            val nextNoon = Instant.parse("2026-09-23T12:00:00Z")
            clock.now = nextNoon
            repository.insert(encounterAt(nextNoon).copy(id = "this-morning"))

            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // An encounter's day is where it happened, not where the phone is now: logged at 01:00 in an
    // offset three hours ahead of UTC, it belongs to the 23rd there while UTC is still on the 22nd.
    @Test
    fun `a cat keeps the day of the offset it was captured in`() = runTest {
        val pastMidnightAbroad = Instant.parse("2026-09-22T22:00:00Z")
        repository.insert(encounterAt(pastMidnightAbroad, tzOffsetMinutes = 180).copy(id = "abroad"))

        val deviceOnTheTwentySecond = countIn(TimeZone.UTC, now = pastMidnightAbroad)
        val deviceOnTheTwentyThird = countIn(TimeZone.of("UTC+3"), now = pastMidnightAbroad)

        assertEquals(0, deviceOnTheTwentySecond().first())
        assertEquals(1, deviceOnTheTwentyThird().first())
    }

    @Test
    fun `a new cat today moves the count, and an unrelated write does not re-emit`() = runTest {
        val observe = countIn(TimeZone.UTC)
        observe().test {
            assertEquals(0, awaitItem())

            repository.insert(encounterAt(Noon).copy(id = "first"))
            assertEquals(1, awaitItem())

            repository.insert(encounterAt(Instant.parse("2026-09-20T12:00:00Z")).copy(id = "old"))
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
