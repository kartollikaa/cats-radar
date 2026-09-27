package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.inShotOf
import dev.catsradar.domain.testing.withPhoto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class UndoImportTest {
    @Test
    fun `soft deletes the whole run in one write with one instant from the clock`() = runTest {
        val now = Instant.parse("2026-09-23T12:00:00Z")
        val repository = FakeEncounterRepository()

        UndoImport(repository, FakeClock(now), analytics = RecordingAnalytics())(listOf("a", "b", "c"))

        assertEquals(listOf(listOf("a", "b", "c") to now), repository.softDeleteAllCalls)
        assertEquals(emptyList(), repository.softDeleteCalls)
    }

    @Test
    fun undoingAnImportKeepsACatAddedToItsPhoto() = runTest {
        val now = Instant.parse("2026-09-23T12:00:00Z")
        val repository = FakeEncounterRepository()
        val source = encounterFixture("imported", now)
            .copy(origin = EncounterOrigin.GALLERY)
            .withPhoto("imported.jpg", "imported_thumb.jpg")
        val added = encounterFixture("added", now)
            .copy(origin = EncounterOrigin.GALLERY, coat = CatCoat.BLACK)
            .withPhoto("added.jpg", "added_thumb.jpg")
            .inShotOf(source.cover!!.shotId)
        repository.insert(source)
        repository.insert(added)

        UndoImport(repository, FakeClock(now), RecordingAnalytics())(listOf(source.id))

        assertEquals(null, repository.observeById(source.id).first())
        assertEquals(added, repository.observeById(added.id).first())
    }
}
