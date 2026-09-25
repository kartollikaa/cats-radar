package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.presentation.counter.FakeDeviceIdProvider
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import dev.catsradar.presentation.encounters.withPhoto
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PhotoViewerStateMapperTest {

    private val mapper = PhotoViewerStateMapper(
        FakeDateTimeFormatter(),
        FakePhotoStorage(root = "/data/photos"),
        FakeDeviceIdProvider(INSTALL),
    )

    @Test
    fun `a cat with a photo shows the app's copy by its absolute path, with when it was taken`() {
        val cat = photographedCat()

        assertEquals(
            PhotoViewerState.Showing(
                photoPath = "/data/photos/cat-1.jpg",
                timeLabel = "2026-09-22T10:00:00Z",
                dayLabel = "2026-09-22",
            ),
            mapper.map(cat, TODAY),
        )
    }

    @Test
    fun `the day comes from the cat's own offset, not the phone's`() {
        // Past midnight at +10:00 while still the 22nd in UTC and in any zone from -12:00 to +09:00.
        val loggedAhead = photographedCat().copy(
            occurredAt = Instant.parse("2026-09-22T14:30:00Z"),
            tzOffsetMinutes = 600,
        )

        assertEquals("2026-09-23", mapper.map(loggedAhead, TODAY)?.dayLabel)
    }

    @Test
    fun `a cat without a photo has nothing to show`() {
        assertEquals(null, mapper.map(encounterFixture("cat-1", OCCURRED), TODAY))
    }

    @Test
    fun `an original this install saved to the gallery is offered there`() {
        val cat = photographedCat(galleryUri = SAVED)

        assertEquals(true, mapper.map(cat, TODAY)?.opensInGallery)
    }

    @Test
    fun `a photo this install picked from the gallery is offered there`() {
        val cat = photographedCat(sourceMediaUri = "content://media/external/images/media/17")

        assertEquals(true, mapper.map(cat, TODAY)?.opensInGallery)
    }

    @Test
    fun `an original recorded by another install is not offered here`() {
        val cat = photographedCat(galleryUri = SAVED, deviceId = "another-install")

        assertEquals(false, mapper.map(cat, TODAY)?.opensInGallery)
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

        assertEquals(true, mapper.map(attachedHere, TODAY)?.opensInGallery)
        assertEquals(false, onThatInstall.map(attachedHere, TODAY)?.opensInGallery)
    }

    @Test
    fun `a photo with no original in the gallery offers nothing there`() {
        assertEquals(false, mapper.map(photographedCat(), TODAY)?.opensInGallery)
    }

    private fun photographedCat(
        galleryUri: String? = null,
        sourceMediaUri: String? = null,
        deviceId: String = INSTALL,
    ) = encounterFixture("cat-1", OCCURRED)
        .copy(kind = EncounterKind.PHOTO, deviceId = deviceId)
        .withPhoto(photoPath = "cat-1.jpg", galleryUri = galleryUri, sourceMediaUri = sourceMediaUri)

    private companion object {
        const val INSTALL = "device-1"
        const val SAVED = "content://media/external/images/media/42"
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
        val TODAY = LocalDate(2026, 9, 25)
    }
}
