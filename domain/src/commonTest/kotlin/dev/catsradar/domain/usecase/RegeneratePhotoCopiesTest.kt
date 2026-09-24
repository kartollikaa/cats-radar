package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeImageResizer
import dev.catsradar.domain.testing.FakeSettingsRepository
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class RegeneratePhotoCopiesTest {

    private val repository = FakeEncounterRepository()
    private val resizer = FakeImageResizer()
    private val settings = FakeSettingsRepository()

    private fun regenerate(imageResizer: ImageResizer = resizer) =
        RegeneratePhotoCopies(repository, imageResizer, settings)

    private fun cameraPhoto(id: String, galleryUri: String?): Encounter = encounterFixture(id, OCCURRED).copy(
        kind = EncounterKind.PHOTO,
        origin = EncounterOrigin.CAMERA,
        photoPath = "$id.jpg",
        thumbPath = "${id}_thumb.jpg",
        galleryUri = galleryUri,
    )

    @Test
    fun `each photo with a gallery original has its copies rebuilt from it under its own id`() = runTest {
        repository.insert(cameraPhoto("a", URI_A))
        repository.insert(cameraPhoto("b", URI_B).copy(deletedAt = DELETED))

        regenerate()()

        assertEquals(listOf(URI_A to "a", URI_B to "b"), resizer.requests)
    }

    @Test
    fun `a photo with no gallery original is left alone`() = runTest {
        repository.insert(cameraPhoto("imported", galleryUri = null).copy(origin = EncounterOrigin.GALLERY))
        repository.insert(cameraPhoto("gallery-saving-off", galleryUri = null))
        repository.insert(encounterFixture("tally", OCCURRED))

        regenerate()()

        assertEquals(emptyList(), resizer.requests)
    }

    @Test
    fun `no row is rewritten, whatever paths the rebuilt copies come back with`() = runTest {
        repository.insert(cameraPhoto("a", URI_A))
        resizer.result = StoredPhoto(photoPath = "elsewhere.jpg", thumbPath = null)
        val before = repository.loadEvery()

        regenerate()()

        assertEquals(before, repository.loadEvery())
    }

    @Test
    fun `an original that cannot be read does not stop the others`() = runTest {
        repository.insert(cameraPhoto("gone", URI_GONE))
        repository.insert(cameraPhoto("a", URI_A))
        resizer.undecodable += URI_GONE

        regenerate()()

        assertEquals(listOf(URI_GONE to "gone", URI_A to "a"), resizer.requests)
    }

    @Test
    fun `once a pass has finished, even with an unreadable original, a later start rebuilds nothing`() = runTest {
        repository.insert(cameraPhoto("gone", URI_GONE))
        repository.insert(cameraPhoto("a", URI_A))
        resizer.undecodable += URI_GONE
        regenerate()()
        resizer.requests.clear()

        regenerate()()

        assertEquals(emptyList(), resizer.requests)
    }

    @Test
    fun `a pass cut short is run again from the start`() = runTest {
        repository.insert(cameraPhoto("a", URI_A))
        repository.insert(cameraPhoto("b", URI_B))
        val cutShort = object : ImageResizer {
            override suspend fun store(sourceUri: String, encounterId: String): StoredPhoto? =
                if (sourceUri == URI_B) error("process killed") else resizer.store(sourceUri, encounterId)
        }
        assertFailsWith<IllegalStateException> { regenerate(cutShort)() }
        resizer.requests.clear()

        regenerate()()

        assertEquals(listOf(URI_A to "a", URI_B to "b"), resizer.requests)
    }

    private companion object {
        val OCCURRED = Instant.parse("2026-09-23T06:52:00Z")
        val DELETED = Instant.parse("2026-09-23T08:00:00Z")
        const val URI_A = "content://media/external/images/media/1"
        const val URI_B = "content://media/external/images/media/2"
        const val URI_GONE = "content://media/external/images/media/3"
    }
}
