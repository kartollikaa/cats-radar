package dev.catsradar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class GalleryLinkTest {

    @Test
    fun `an original this install saved to the gallery is a link`() {
        val photo = photo(galleryUri = SAVED, deviceId = THIS_INSTALL)

        assertEquals(GalleryLink(uri = SAVED, ownedByApp = true), photo.galleryLink(THIS_INSTALL))
    }

    @Test
    fun `a photo with no original in the gallery has no link`() {
        val photo = photo(galleryUri = null, deviceId = THIS_INSTALL)

        assertEquals(null, photo.galleryLink(THIS_INSTALL))
    }

    @Test
    fun `an original recorded by another install is never a link here`() {
        val photo = photo(galleryUri = SAVED, deviceId = "another-install")

        assertEquals(null, photo.galleryLink(THIS_INSTALL))
    }

    @Test
    fun `a photo this install picked from the gallery is a link to the item, one the app does not own`() {
        val photo = photo(sourceMediaUri = PICKED, deviceId = THIS_INSTALL)

        assertEquals(GalleryLink(uri = PICKED, ownedByApp = false), photo.galleryLink(THIS_INSTALL))
    }

    @Test
    fun `a photo another install picked is never a link here`() {
        val photo = photo(sourceMediaUri = PICKED, deviceId = "another-install")

        assertEquals(null, photo.galleryLink(THIS_INSTALL))
    }

    @Test
    fun `the original the app saved wins over a picked item on the same photo`() {
        val photo = photo(galleryUri = SAVED, sourceMediaUri = PICKED, deviceId = THIS_INSTALL)

        assertEquals(GalleryLink(uri = SAVED, ownedByApp = true), photo.galleryLink(THIS_INSTALL))
    }

    private fun photo(deviceId: String, galleryUri: String? = null, sourceMediaUri: String? = null) = EncounterPhoto(
        id = "cat-1",
        encounterId = "cat-1",
        photoPath = "cat-1.jpg",
        thumbPath = "cat-1_thumb.jpg",
        galleryUri = galleryUri,
        sourceMediaUri = sourceMediaUri,
        sourceDigest = null,
        deviceId = deviceId,
        addedAt = OCCURRED,
        shotId = "cat-1",
    )

    private companion object {
        const val THIS_INSTALL = "install-1"
        const val SAVED = "content://media/external/images/media/42"
        const val PICKED = "content://media/external/images/media/17"
        val OCCURRED = Instant.parse("2026-09-24T10:00:00Z")
    }
}
