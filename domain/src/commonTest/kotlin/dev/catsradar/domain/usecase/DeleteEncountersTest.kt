package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.DeletedBatch
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DeleteEncountersTest {
    private val now = Instant.parse("2026-09-23T10:00:00Z")

    @Test
    fun `soft deletes every given id with one instant taken from the clock`() = runTest {
        val repository = FakeEncounterRepository()
        val deleteEncounters = DeleteEncounters(repository, FakeClock(now))

        val batch = deleteEncounters(listOf("a", "b", "c"))

        assertEquals(DeletedBatch(ids = listOf("a", "b", "c"), deletedAt = now), batch)
        assertEquals(listOf(listOf("a", "b", "c") to now), repository.softDeleteAllCalls)
    }

    @Test
    fun `undo hands the batch's own ids and instant back to the repository`() = runTest {
        val repository = FakeEncounterRepository()
        val batch = DeletedBatch(ids = listOf("a", "b"), deletedAt = now)

        UndoDeleteEncounters(repository)(batch)

        assertEquals(listOf(listOf("a", "b") to now), repository.undoDeleteAllCalls)
    }

    @Test
    fun `a deleted batch leaves the list and its undo brings back exactly that batch`() = runTest {
        val repository = FakeEncounterRepository()
        listOf("a", "b", "c").forEach { repository.insert(encounterFixture(it, Instant.parse("2026-09-23T09:00:00Z"))) }

        val batch = DeleteEncounters(repository, FakeClock(now))(listOf("a", "b"))
        assertEquals(listOf("c"), repository.observeAll().first().map { it.id })

        UndoDeleteEncounters(repository)(batch)
        assertEquals(listOf("a", "b", "c"), repository.observeAll().first().map { it.id })
    }
}
