package dev.catsradar.domain.usecase

import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class UndoLastTallyTest {
    @Test
    fun `soft deletes exactly the given id using the current time`() = runTest {
        val now = Instant.parse("2026-09-22T10:05:00Z")
        val repository = FakeEncounterRepository()
        val undoLastTally = UndoLastTally(repository, FakeClock(now), analytics = RecordingAnalytics())

        undoLastTally("target-id")

        assertEquals(listOf("target-id" to now), repository.softDeleteCalls)
    }
}
