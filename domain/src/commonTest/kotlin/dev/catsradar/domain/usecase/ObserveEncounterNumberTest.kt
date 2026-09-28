package dev.catsradar.domain.usecase

import app.cash.turbine.test
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ObserveEncounterNumberTest {

    private val repository = FakeEncounterRepository()
    private val observeNumber = ObserveEncounterNumber(repository)

    @Test
    fun `live cats are numbered oldest first`() = runTest {
        repository.insert(encounterFixture("late", BASE + 20.minutes))
        repository.insert(encounterFixture("early", BASE))
        repository.insert(encounterFixture("middle", BASE + 10.minutes))

        assertEquals(listOf(1, 2, 3), listOf("early", "middle", "late").map { observeNumber(it).first() })
    }

    @Test
    fun `cats logged at the same instant are numbered by the smaller id first`() = runTest {
        repository.insert(encounterFixture("cat-b", BASE))
        repository.insert(encounterFixture("cat-a", BASE))

        assertEquals(listOf(1, 2), listOf("cat-a", "cat-b").map { observeNumber(it).first() })
    }

    @Test
    fun `a delete renumbers the cats after it, and the deleted cat has no number until the undo`() = runTest {
        repository.insert(encounterFixture("first", BASE))
        repository.insert(encounterFixture("second", BASE + 10.minutes))
        repository.insert(encounterFixture("third", BASE + 20.minutes))

        observeNumber("third").test {
            assertEquals(3, awaitItem())

            repository.softDelete("second", deletedAt = BASE + 30.minutes)
            assertEquals(2, awaitItem())

            repository.undoDelete("second")
            assertEquals(3, awaitItem())
        }
        repository.softDelete("second", deletedAt = BASE + 40.minutes)
        assertNull(observeNumber("second").first())
    }

    @Test
    fun `an unknown id has no number`() = runTest {
        repository.insert(encounterFixture("first", BASE))

        assertNull(observeNumber("never-logged").first())
    }

    @Test
    fun `a write that does not move the cat emits nothing new`() = runTest {
        repository.insert(encounterFixture("first", BASE))
        repository.insert(encounterFixture("second", BASE + 10.minutes))

        observeNumber("second").test {
            assertEquals(2, awaitItem())

            repository.setCoat("first", CatCoat.GINGER, updatedAt = BASE + 20.minutes)
            repository.insert(encounterFixture("later", BASE + 30.minutes))
            expectNoEvents()

            repository.insert(encounterFixture("imported", BASE + 5.minutes))
            assertEquals(3, awaitItem())
        }
    }

    private companion object {
        val BASE = Instant.parse("2026-09-20T10:00:00Z")
    }
}
