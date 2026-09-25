package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.presentation.counter.FakeDeviceIdProvider
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import dev.catsradar.presentation.encounters.withPhoto
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PhotoViewerStateMapperTest {

    private val mapper = PhotoViewerStateMapper(
        FakeDateTimeFormatter(),
        FakePhotoStorage(root = "/data/photos"),
        FakeDeviceIdProvider(INSTALL),
    )

    @Test
    fun `a cat with a photo shows the app's copy by its absolute path, with when it was taken`() {
        assertEquals(
            PhotoViewerState.Showing(
                photos = persistentListOf(ViewerPhoto(id = "cat-1", path = "/data/photos/cat-1.jpg")),
                firstPage = 0,
                timeLabel = "2026-09-22T10:00:00Z",
                dayLabel = "2026-09-22",
            ),
            mapper.map(photographedCat(), TODAY, openedOn = null),
        )
    }

    @Test
    fun `every photo of the cat is shown, oldest first, each with its own copy and link`() {
        val cat = threePhotoCat()

        assertEquals(
            persistentListOf(
                ViewerPhoto(id = "cat-1", path = "/data/photos/cat-1.jpg"),
                ViewerPhoto(id = "second", path = "/data/photos/second.jpg", opensInGallery = true),
                ViewerPhoto(id = "third", path = "/data/photos/third.jpg"),
            ),
            mapper.map(cat, TODAY, openedOn = null)?.photos,
        )
    }

    @Test
    fun `the viewer opens on the photo it was opened for`() {
        assertEquals(2, mapper.map(threePhotoCat(), TODAY, openedOn = "third")?.firstPage)
    }

    @Test
    fun `opened for no photo, or for one the cat does not have, the viewer opens on the cover`() {
        assertEquals(0, mapper.map(threePhotoCat(), TODAY, openedOn = null)?.firstPage)
        assertEquals(0, mapper.map(threePhotoCat(), TODAY, openedOn = "gone")?.firstPage)
    }

    @Test
    fun `the day comes from the cat's own offset, not the phone's`() {
        // Past midnight at +10:00 while still the 22nd in UTC and in any zone from -12:00 to +09:00.
        val loggedAhead = photographedCat().copy(
            occurredAt = Instant.parse("2026-09-22T14:30:00Z"),
            tzOffsetMinutes = 600,
        )

        assertEquals("2026-09-23", mapper.map(loggedAhead, TODAY, openedOn = null)?.dayLabel)
    }

    @Test
    fun `a cat without a photo has nothing to show`() {
        assertEquals(null, mapper.map(encounterFixture("cat-1", OCCURRED), TODAY, openedOn = null))
    }

    @Test
    fun `an original this install saved to the gallery is offered there`() {
        assertEquals(true, galleryOffered(photographedCat(galleryUri = SAVED)))
    }

    @Test
    fun `a photo this install picked from the gallery is offered there`() {
        assertEquals(true, galleryOffered(photographedCat(sourceMediaUri = "content://media/external/images/media/17")))
    }

    @Test
    fun `an original recorded by another install is not offered here`() {
        assertEquals(false, galleryOffered(photographedCat(galleryUri = SAVED, deviceId = "another-install")))
    }

    @Test
    fun `a photo given here to a cat another install logged is offered here and not on that install`() {
        val foreign = photographedCat(galleryUri = SAVED, deviceId = "another-install")
        val attachedHere = foreign.copy(photos = foreign.photos.map { it.copy(deviceId = INSTALL) })
        val onThatInstall = PhotoViewerStateMapper(
            FakeDateTimeFormatter(),
            FakePhotoStorage(root = "/data/photos"),
            FakeDeviceIdProvider("another-install"),
        )

        assertEquals(true, galleryOffered(attachedHere))
        assertEquals(false, onThatInstall.map(attachedHere, TODAY, openedOn = null)?.photos?.single()?.opensInGallery)
    }

    @Test
    fun `a photo with no original in the gallery offers nothing there`() {
        assertEquals(false, galleryOffered(photographedCat()))
    }

    private fun galleryOffered(cat: Encounter): Boolean? =
        mapper.map(cat, TODAY, openedOn = null)?.photos?.single()?.opensInGallery

    private fun photographedCat(
        galleryUri: String? = null,
        sourceMediaUri: String? = null,
        deviceId: String = INSTALL,
    ) = encounterFixture("cat-1", OCCURRED)
        .copy(kind = EncounterKind.PHOTO, deviceId = deviceId)
        .withPhoto(photoPath = "cat-1.jpg", galleryUri = galleryUri, sourceMediaUri = sourceMediaUri)

    private fun threePhotoCat(): Encounter {
        val cat = photographedCat()
        val cover = cat.photos.single()
        val second = cover.copy(
            id = "second",
            photoPath = "second.jpg",
            galleryUri = SAVED,
            addedAt = OCCURRED + 1.minutes
        )
        val third = cover.copy(id = "third", photoPath = "third.jpg", addedAt = OCCURRED + 2.minutes)
        return cat.copy(photos = listOf(cover, second, third))
    }

    private companion object {
        const val INSTALL = "device-1"
        const val SAVED = "content://media/external/images/media/42"
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
        val TODAY = LocalDate(2026, 9, 25)
    }
}
