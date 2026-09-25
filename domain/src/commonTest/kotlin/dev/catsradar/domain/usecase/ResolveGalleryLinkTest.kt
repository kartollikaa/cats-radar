package dev.catsradar.domain.usecase

import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeGalleryItems
import dev.catsradar.domain.testing.encounterAt
import dev.catsradar.domain.testing.withPhoto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ResolveGalleryLinkTest {

    private val galleryItems = FakeGalleryItems()
    private val resolve = ResolveGalleryLink(galleryItems, FakeDeviceIdProvider(THIS_INSTALL))

    @Test
    fun `an original the gallery still holds opens`() = runTest {
        galleryItems.present += SAVED

        assertEquals(GalleryTarget.Open(uri = SAVED), resolve(savedPhoto()))
    }

    @Test
    fun `an original deleted from the gallery is gone`() = runTest {
        assertEquals(GalleryTarget.Gone, resolve(savedPhoto()))
        assertEquals(listOf(SAVED), galleryItems.asked)
    }

    @Test
    fun `a photo with no link has nothing to open, and the gallery is never asked`() = runTest {
        assertEquals(GalleryTarget.Unavailable, resolve(savedPhoto().copy(galleryUri = null)))
        assertEquals(emptyList(), galleryItems.asked)
    }

    @Test
    fun `a picked item opens unchecked, since without photo access the app cannot see it`() = runTest {
        val picked = savedPhoto().copy(galleryUri = null, sourceMediaUri = PICKED)

        assertEquals(GalleryTarget.Open(uri = PICKED), resolve(picked))
        assertEquals(emptyList(), galleryItems.asked)
    }

    private fun savedPhoto() =
        encounterAt(OCCURRED).copy(id = "cat-1", deviceId = THIS_INSTALL).withPhoto(galleryUri = SAVED).photos.single()

    private companion object {
        const val THIS_INSTALL = "install-1"
        const val SAVED = "content://media/external/images/media/42"
        const val PICKED = "content://media/external/images/media/17"
        val OCCURRED = Instant.parse("2026-09-24T10:00:00Z")
    }
}
