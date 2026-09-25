package dev.catsradar.domain.model

import dev.catsradar.domain.testing.encounterAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class GalleryLinkTest {

    @Test
    fun `an original this install saved to the gallery is a link`() {
        val cat = encounterAt(OCCURRED).copy(galleryUri = SAVED, deviceId = THIS_INSTALL)

        assertEquals(GalleryLink(uri = SAVED, ownedByApp = true), cat.galleryLink(THIS_INSTALL))
    }

    @Test
    fun `a cat with no original in the gallery has no link`() {
        val cat = encounterAt(OCCURRED).copy(galleryUri = null, deviceId = THIS_INSTALL)

        assertEquals(null, cat.galleryLink(THIS_INSTALL))
    }

    @Test
    fun `an original recorded by another install is never a link here`() {
        val cat = encounterAt(OCCURRED).copy(galleryUri = SAVED, deviceId = "another-install")

        assertEquals(null, cat.galleryLink(THIS_INSTALL))
    }

    @Test
    fun `a photo this install picked from the gallery is a link to the item, one the app does not own`() {
        val cat = encounterAt(OCCURRED).copy(sourceMediaUri = PICKED, deviceId = THIS_INSTALL)

        assertEquals(GalleryLink(uri = PICKED, ownedByApp = false), cat.galleryLink(THIS_INSTALL))
    }

    @Test
    fun `a photo another install picked is never a link here`() {
        val cat = encounterAt(OCCURRED).copy(sourceMediaUri = PICKED, deviceId = "another-install")

        assertEquals(null, cat.galleryLink(THIS_INSTALL))
    }

    @Test
    fun `the original the app saved wins over a picked item on the same cat`() {
        val cat = encounterAt(OCCURRED).copy(galleryUri = SAVED, sourceMediaUri = PICKED, deviceId = THIS_INSTALL)

        assertEquals(GalleryLink(uri = SAVED, ownedByApp = true), cat.galleryLink(THIS_INSTALL))
    }

    private companion object {
        const val THIS_INSTALL = "install-1"
        const val SAVED = "content://media/external/images/media/42"
        const val PICKED = "content://media/external/images/media/17"
        val OCCURRED = Instant.parse("2026-09-24T10:00:00Z")
    }
}
