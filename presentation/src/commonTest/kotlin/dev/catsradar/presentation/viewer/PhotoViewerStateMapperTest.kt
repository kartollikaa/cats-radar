package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.presentation.counter.FakeDeviceIdProvider
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PhotoViewerStateMapperTest {

    private val mapper = PhotoViewerStateMapper(FakePhotoStorage(root = "/data/photos"), FakeDeviceIdProvider(INSTALL))

    @Test
    fun `a cat with a photo shows the app's copy by its absolute path`() {
        val cat = photographedCat()

        assertEquals(PhotoViewerState.Showing(photoPath = "/data/photos/cat-1.jpg"), mapper.map(cat))
    }

    @Test
    fun `a cat without a photo has nothing to show`() {
        assertEquals(null, mapper.map(encounterFixture("cat-1", OCCURRED)))
    }

    @Test
    fun `an original this install saved to the gallery is offered there`() {
        val cat = photographedCat().copy(galleryUri = SAVED)

        assertEquals(
            PhotoViewerState.Showing(photoPath = "/data/photos/cat-1.jpg", opensInGallery = true),
            mapper.map(cat),
        )
    }

    @Test
    fun `an original recorded by another install is not offered here`() {
        val cat = photographedCat().copy(galleryUri = SAVED, deviceId = "another-install")

        assertEquals(false, mapper.map(cat)?.opensInGallery)
    }

    @Test
    fun `a photo with no original in the gallery offers nothing there`() {
        assertEquals(false, mapper.map(photographedCat())?.opensInGallery)
    }

    private fun photographedCat() =
        encounterFixture("cat-1", OCCURRED).copy(kind = EncounterKind.PHOTO, photoPath = "cat-1.jpg")

    private companion object {
        const val INSTALL = "device-1"
        const val SAVED = "content://media/external/images/media/42"
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
