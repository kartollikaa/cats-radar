package dev.catsradar.domain.usecase

import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeGalleryItems
import dev.catsradar.domain.testing.encounterAt
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

        assertEquals(GalleryTarget.Open(uri = SAVED), resolve(savedCat()))
    }

    @Test
    fun `an original deleted from the gallery is gone`() = runTest {
        assertEquals(GalleryTarget.Gone, resolve(savedCat()))
        assertEquals(listOf(SAVED), galleryItems.asked)
    }

    @Test
    fun `a cat with no link has nothing to open, and the gallery is never asked`() = runTest {
        assertEquals(GalleryTarget.Unavailable, resolve(savedCat().copy(galleryUri = null)))
        assertEquals(emptyList(), galleryItems.asked)
    }

    @Test
    fun `a picked item opens without asking the gallery, which the app cannot read`() = runTest {
        val picked = savedCat().copy(galleryUri = null, sourceMediaUri = PICKED)

        assertEquals(GalleryTarget.Open(uri = PICKED), resolve(picked))
        assertEquals(emptyList(), galleryItems.asked)
    }

    private fun savedCat() = encounterAt(OCCURRED).copy(id = "cat-1", galleryUri = SAVED, deviceId = THIS_INSTALL)

    private companion object {
        const val THIS_INSTALL = "install-1"
        const val SAVED = "content://media/external/images/media/42"
        const val PICKED = "content://media/external/images/media/17"
        val OCCURRED = Instant.parse("2026-09-24T10:00:00Z")
    }
}
