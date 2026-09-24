package dev.catsradar.domain.usecase

import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeGalleryItems
import dev.catsradar.domain.testing.encounterAt
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ResolveGalleryLinkTest {

    private val repository = FakeEncounterRepository()
    private val galleryItems = FakeGalleryItems()
    private val resolve = ResolveGalleryLink(repository, galleryItems, FakeDeviceIdProvider(THIS_INSTALL))

    @Test
    fun `an original the gallery still holds opens with a read grant`() = runTest {
        repository.insert(savedCat())
        galleryItems.present += SAVED

        assertEquals(GalleryTarget.Open(uri = SAVED, grantRead = true), resolve(ID))
    }

    @Test
    fun `an original deleted from the gallery is gone`() = runTest {
        repository.insert(savedCat())

        assertEquals(GalleryTarget.Gone, resolve(ID))
        assertEquals(listOf(SAVED), galleryItems.asked)
    }

    @Test
    fun `a cat with no link has nothing to open, and the gallery is never asked`() = runTest {
        repository.insert(savedCat().copy(galleryUri = null))

        assertEquals(GalleryTarget.Unavailable, resolve(ID))
        assertEquals(emptyList(), galleryItems.asked)
    }

    @Test
    fun `an id with no live cat has nothing to open`() = runTest {
        assertEquals(GalleryTarget.Unavailable, resolve(ID))
        assertEquals(emptyList(), galleryItems.asked)
    }

    private fun savedCat() = encounterAt(OCCURRED).copy(id = ID, galleryUri = SAVED, deviceId = THIS_INSTALL)

    private companion object {
        const val ID = "cat-1"
        const val THIS_INSTALL = "install-1"
        const val SAVED = "content://media/external/images/media/42"
        val OCCURRED = Instant.parse("2026-09-24T10:00:00Z")
    }
}
