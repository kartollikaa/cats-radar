package dev.catsradar.domain.usecase

import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.withPhoto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FindCatThumbnailsTest {

    private val at = Instant.parse("2026-09-28T10:00:00Z")

    private suspend fun repositoryWith(vararg cats: Pair<String, String?>): FakeEncounterRepository =
        FakeEncounterRepository().apply {
            cats.forEach { (id, thumb) ->
                val cat = encounterFixture(id, at)
                insert(if (thumb == null) cat else cat.withPhoto(thumbPath = thumb))
            }
        }

    @Test
    fun `the thumbnails come in the order of the cats asked for`() = runTest {
        val repository = repositoryWith("a" to "a.jpg", "b" to "b.jpg", "c" to "c.jpg")

        assertEquals(listOf("c.jpg", "a.jpg"), FindCatThumbnails(repository)(listOf("c", "a"), limit = 3))
    }

    @Test
    fun `a cat without a thumbnail, or not found, is skipped rather than ending the list`() = runTest {
        val repository = repositoryWith("a" to null, "b" to "b.jpg", "c" to "c.jpg")

        assertEquals(listOf("b.jpg", "c.jpg"), FindCatThumbnails(repository)(listOf("a", "gone", "b", "c"), limit = 3))
    }

    @Test
    fun `no more than the limit are returned`() = runTest {
        val repository = repositoryWith("a" to "a.jpg", "b" to "b.jpg", "c" to "c.jpg", "d" to "d.jpg")

        val thumbnails = FindCatThumbnails(repository)(listOf("a", "b", "c", "d"), limit = 3)

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), thumbnails)
    }
}
