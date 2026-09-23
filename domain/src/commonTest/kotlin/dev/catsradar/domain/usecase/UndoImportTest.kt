package dev.catsradar.domain.usecase

import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class UndoImportTest {
    @Test
    fun `soft deletes the whole run in one write with one instant from the clock`() = runTest {
        val now = Instant.parse("2026-09-23T12:00:00Z")
        val repository = FakeEncounterRepository()

        UndoImport(repository, FakeClock(now))(listOf("a", "b", "c"))

        assertEquals(listOf(listOf("a", "b", "c") to now), repository.softDeleteAllCalls)
        assertEquals(emptyList(), repository.softDeleteCalls)
    }
}
