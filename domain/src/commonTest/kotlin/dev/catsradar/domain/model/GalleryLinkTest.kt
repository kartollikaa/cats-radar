package dev.catsradar.domain.model

import dev.catsradar.domain.testing.encounterAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class GalleryLinkTest {

    @Test
    fun `an original this install saved to the gallery is a link`() {
        val cat = encounterAt(OCCURRED).copy(galleryUri = SAVED, deviceId = THIS_INSTALL)

        assertEquals(GalleryLink(uri = SAVED), cat.galleryLink(THIS_INSTALL))
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

    private companion object {
        const val THIS_INSTALL = "install-1"
        const val SAVED = "content://media/external/images/media/42"
        val OCCURRED = Instant.parse("2026-09-24T10:00:00Z")
    }
}
