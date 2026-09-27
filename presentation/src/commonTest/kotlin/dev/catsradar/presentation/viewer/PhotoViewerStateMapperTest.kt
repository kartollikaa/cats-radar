package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.usecase.GalleryTarget
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

    private val mapper = PhotoViewerStateMapper(FakeDateTimeFormatter(), FakePhotoStorage(root = "/data/photos"))

    @Test
    fun `a cat with a photo shows the app's copy by its absolute path, with when it was taken`() {
        assertEquals(
            PhotoViewerState.Showing(
                photos = persistentListOf(ViewerPhoto(id = "cat-1", path = "/data/photos/cat-1.jpg")),
                firstPage = 0,
                timeLabel = "2026-09-22T10:00:00Z",
                dayLabel = "2026-09-22",
            ),
            map(photographedCat(), openedOn = null),
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
            map(cat, targets = mapOf("second" to GalleryTarget.Open(SAVED)))?.photos,
        )
    }

    @Test
    fun `the viewer opens on the photo it was opened for`() {
        assertEquals(2, map(threePhotoCat(), openedOn = "third")?.firstPage)
    }

    @Test
    fun `opened for no photo, or for one the cat does not have, the viewer opens on the cover`() {
        assertEquals(0, map(threePhotoCat(), openedOn = null)?.firstPage)
        assertEquals(0, map(threePhotoCat(), openedOn = "gone")?.firstPage)
    }

    @Test
    fun `the day comes from the cat's own offset, not the phone's`() {
        // Past midnight at +10:00 while still the 22nd in UTC and in any zone from -12:00 to +09:00.
        val loggedAhead = photographedCat().copy(
            occurredAt = Instant.parse("2026-09-22T14:30:00Z"),
            tzOffsetMinutes = 600,
        )

        assertEquals("2026-09-23", map(loggedAhead, openedOn = null)?.dayLabel)
    }

    @Test
    fun `a cat without a photo has nothing to show`() {
        assertEquals(null, map(encounterFixture("cat-1", OCCURRED), openedOn = null))
    }

    @Test
    fun `a photo is offered in the gallery only when its target opens there`() {
        val offered = listOf(
            GalleryTarget.Open(SAVED),
            GalleryTarget.Gone,
            GalleryTarget.Unavailable,
            null,
        ).map { target -> galleryOffered(target) }

        assertEquals(listOf(true, false, false, false), offered)
    }

    @Test
    fun `offering the gallery changes only the offer, not a removal being asked about`() {
        val showing = checkNotNull(map(photographedCat(), openedOn = null))
            .copy(removingPhotoId = "cat-1")

        val offered = mapper.offerGallery(showing, mapOf("cat-1" to GalleryTarget.Open(SAVED)))

        assertEquals(
            showing.copy(photos = persistentListOf(showing.photos.single().copy(opensInGallery = true))),
            offered,
        )
    }

    private fun map(cat: Encounter, openedOn: String? = null, targets: Map<String, GalleryTarget> = emptyMap()) =
        mapper.map(cat, TODAY, openedOn, targets)

    private fun galleryOffered(target: GalleryTarget?): Boolean? {
        val targets = target?.let { mapOf("cat-1" to it) }.orEmpty()
        return map(photographedCat(galleryUri = SAVED), targets = targets)
            ?.photos?.single()?.opensInGallery
    }

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
