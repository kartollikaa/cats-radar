package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.oldestFirst
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.RecordingPhotoStorage
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.inShotOf
import dev.catsradar.domain.testing.withPhoto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RemovePhotoTest {
    private val repository = FakeEncounterRepository()
    private val storage = RecordingPhotoStorage()

    private fun removePhoto(photoStorage: PhotoStorage = storage) =
        RemovePhoto(repository, photoStorage, FakeClock(NOW))

    @Test
    fun `the displayed attachment and only its owned files are removed`() = runTest {
        val cat = encounterFixture(CAT, OCCURRED).withPhoto("first.jpg", "first-thumb.jpg")
        val first = cat.photos.single()
        val second = first.copy(id = SECOND, photoPath = "second.jpg", thumbPath = "second-thumb.jpg")
        repository.insert(cat.copy(photos = listOf(first, second).oldestFirst()))

        assertTrue(removePhoto()(CAT, SECOND))

        assertEquals(listOf("second.jpg", "second-thumb.jpg"), storage.deleted)
        assertEquals(listOf(first), repository.observeById(CAT).first()?.photos)
        assertEquals(NOW, repository.observeById(CAT).first()?.updatedAt)
    }

    @Test
    fun `another cat in the same shot keeps its row and its own files`() = runTest {
        val first = encounterFixture(CAT, OCCURRED).withPhoto("first.jpg", "first-thumb.jpg")
        val another = encounterFixture(OTHER_CAT, OCCURRED)
            .withPhoto("other.jpg", "other-thumb.jpg")
            .inShotOf(first.photos.single().shotId)
        repository.insert(first)
        repository.insert(another)

        assertTrue(removePhoto()(CAT, CAT))

        assertEquals(listOf("first.jpg", "first-thumb.jpg"), storage.deleted)
        assertEquals(another.photos, repository.observeById(OTHER_CAT).first()?.photos)
    }

    @Test
    fun `the files go while the photo row still points at them`() = runTest {
        val rowWasPresent = mutableListOf<Boolean>()
        repository.insert(encounterFixture(CAT, OCCURRED).withPhoto())
        val observingStorage = object : PhotoStorage {
            override fun resolve(relativePath: String) = relativePath
            override suspend fun copy(stored: StoredPhoto, baseName: String): StoredPhoto = error("unused")

            override suspend fun delete(relativePath: String) {
                rowWasPresent += repository.observeById(CAT).first()?.photos?.any { it.id == CAT } == true
            }
        }

        removePhoto(observingStorage)(CAT, CAT)

        assertEquals(listOf(true, true), rowWasPresent)
    }

    @Test
    fun `an unknown photo changes nothing`() = runTest {
        val cat = encounterFixture(CAT, OCCURRED).withPhoto()
        repository.insert(cat)

        assertFalse(removePhoto()(CAT, "unknown"))

        assertEquals(emptyList(), storage.deleted)
        assertEquals(cat, repository.observeById(CAT).first())
    }

    @Test
    fun `a photo belonging to another cat cannot be removed through this cat`() = runTest {
        val first = encounterFixture(CAT, OCCURRED).withPhoto()
        val another = encounterFixture(OTHER_CAT, OCCURRED).withPhoto()
        repository.insert(first)
        repository.insert(another)

        assertFalse(removePhoto()(CAT, OTHER_CAT))

        assertEquals(emptyList(), storage.deleted)
        assertEquals(another, repository.observeById(OTHER_CAT).first())
    }

    @Test
    fun `a deleted cat loses no photo through the viewer operation`() = runTest {
        repository.insert(encounterFixture(CAT, OCCURRED).withPhoto())
        repository.softDelete(CAT, NOW)

        assertFalse(removePhoto()(CAT, CAT))

        assertEquals(emptyList(), storage.deleted)
        assertEquals(1, repository.loadEvery().single().photos.size)
    }

    private companion object {
        const val CAT = "cat"
        const val OTHER_CAT = "other-cat"
        const val SECOND = "second"
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
        val NOW = Instant.parse("2026-09-26T17:00:00Z")
    }
}
