package dev.catsradar.domain.usecase

import app.cash.turbine.test
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class ObserveStatsTest {

    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }

    @Test
    fun `stats handed an encounter list count those cats rather than reading their own`() = runTest {
        val unread = FakeEncounterRepository()
        val observeStats = ObserveStats(unread, clock, TimeZone.UTC, ticks = flowOf(Unit))
        val handed = flowOf(listOf(encounterFixture("a", now), encounterFixture("b", now)))

        observeStats(handed).test {
            assertEquals(2, awaitItem().total)
            awaitComplete()
        }
    }
}
