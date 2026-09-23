package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDigest
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeGallerySaver
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeImageResizer
import dev.catsradar.domain.testing.FakeSettingsRepository
import dev.catsradar.domain.testing.RecordingPhotoStorage
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class AttachPhotoTest {

    private val encounters = FakeEncounterRepository()
    private val settings = FakeSettingsRepository()
    private val resizer = FakeImageResizer()
    private val gallery = FakeGallerySaver()
    private val storage = RecordingPhotoStorage()

    private val attachPhoto = AttachPhoto(
        encounterRepository = encounters,
        settingsRepository = settings,
        imageResizer = resizer,
        digest = FakeDigest(),
        gallerySaver = gallery,
        photoStorage = storage,
        idGenerator = FakeIdGenerator(),
        clock = FakeClock(NOW),
    )

    private val tally: Encounter = encounterFixture(
        ID,
        OCCURRED,
        locationSource = LocationSource.CURRENT_FIX,
        lat = 41.39864,
        lon = 2.17842,
    ).copy(coat = CatCoat.GINGER)

    private suspend fun stored(id: String = ID): Encounter = encounters.loadEvery().first { it.id == id }

    @Test
    fun `a cat without a photo gets the copy, thumbnail and digest, and keeps everything else`() = runTest {
        encounters.insert(tally)

        assertEquals(AttachResult.Attached, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(
            tally.copy(
                photoPath = FakeImageResizer.PHOTO_PATH,
                thumbPath = FakeImageResizer.THUMB_PATH,
                sourceDigest = FakeDigest.SHA,
                updatedAt = NOW,
            ),
            stored(),
        )
    }

    @Test
    fun `the files are named afresh for the attempt, never after the cat`() = runTest {
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.GALLERY)

        assertEquals(listOf("id-1"), resizer.baseNames)
    }

    @Test
    fun `a camera original goes to the gallery when the setting is on`() = runTest {
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.CAMERA)

        assertEquals(1, gallery.calls)
        assertEquals(FakeGallerySaver.URI, stored().galleryUri)
    }

    @Test
    fun `a camera original stays out of the gallery when the setting is off`() = runTest {
        settings.saveOriginals = false
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.CAMERA)

        assertEquals(0, gallery.calls)
        assertEquals(null, stored().galleryUri)
    }

    @Test
    fun `a photo from the gallery is never copied back into it`() = runTest {
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.GALLERY)

        assertEquals(0, gallery.calls)
    }

    @Test
    fun `an undecodable photo leaves the cat as it was and nothing in the gallery`() = runTest {
        encounters.insert(tally)
        resizer.undecodable += SOURCE

        assertEquals(AttachResult.Unreadable, attachPhoto(ID, SOURCE, PhotoSource.CAMERA))

        assertEquals(tally, stored())
        assertEquals(0, gallery.calls)
    }

    @Test
    fun `a cat that already has a photo keeps it and nothing is copied`() = runTest {
        val photo = tally.copy(photoPath = "own.jpg", thumbPath = "own_thumb.jpg")
        encounters.insert(photo)

        assertEquals(AttachResult.NotAttachable, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(photo, stored())
        assertEquals(0, resizer.calls)
    }

    @Test
    fun `a soft-deleted cat is not given a photo, and not brought back`() = runTest {
        val deleted = tally.copy(deletedAt = NOW)
        encounters.insert(deleted)

        assertEquals(AttachResult.NotAttachable, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(deleted, stored())
        assertEquals(0, resizer.calls)
    }

    @Test
    fun `an id nobody knows is not attachable`() = runTest {
        assertEquals(AttachResult.NotAttachable, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))
        assertEquals(0, resizer.calls)
    }

    @Test
    fun `a cat deleted while its photo was being copied keeps no files from the attempt`() = runTest {
        encounters.insert(tally)
        resizer.duringStore = { encounters.softDelete(ID, NOW) }

        assertEquals(AttachResult.NotAttachable, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(tally.copy(deletedAt = NOW), stored())
        assertEquals(listOf(FakeImageResizer.PHOTO_PATH, FakeImageResizer.THUMB_PATH), storage.deleted)
    }

    @Test
    fun `a write that fails removes the files it had written`() = runTest {
        encounters.insert(tally)
        encounters.attachPhotoShouldThrow = IllegalStateException("disk full")

        assertFailsWith<IllegalStateException> { attachPhoto(ID, SOURCE, PhotoSource.GALLERY) }

        assertEquals(listOf(FakeImageResizer.PHOTO_PATH, FakeImageResizer.THUMB_PATH), storage.deleted)
    }

    @Test
    fun `the same photo can go on two cats`() = runTest {
        encounters.insert(tally)
        encounters.insert(tally.copy(id = "cat-2"))

        assertEquals(AttachResult.Attached, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))
        assertEquals(AttachResult.Attached, attachPhoto("cat-2", SOURCE, PhotoSource.GALLERY))
        assertTrue(storage.deleted.isEmpty())
    }

    private companion object {
        const val ID = "cat-1"
        const val SOURCE = "content://picker/1"
        val OCCURRED = Instant.parse("2026-09-21T10:00:00Z")
        val NOW = Instant.parse("2026-09-23T12:00:00Z")
    }
}
