package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.GalleryLink
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.galleryLink
import dev.catsradar.domain.platform.StoredPhoto
import dev.catsradar.domain.testing.FakeClock
import dev.catsradar.domain.testing.FakeDeviceIdProvider
import dev.catsradar.domain.testing.FakeDigest
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeGalleryItemLocator
import dev.catsradar.domain.testing.FakeGallerySaver
import dev.catsradar.domain.testing.FakeIdGenerator
import dev.catsradar.domain.testing.FakeImageResizer
import dev.catsradar.domain.testing.FakeSettingsRepository
import dev.catsradar.domain.testing.RecordingAnalytics
import dev.catsradar.domain.testing.RecordingPhotoStorage
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.withPhoto
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
    private val locator = FakeGalleryItemLocator(mapOf(SOURCE to PHONE_ITEM))
    private val storage = RecordingPhotoStorage()
    private val digest = FakeDigest()

    private val attachPhoto = AttachPhoto(
        encounterRepository = encounters,
        settingsRepository = settings,
        imageResizer = resizer,
        digest = digest,
        gallerySaver = gallery,
        galleryItemLocator = locator,
        photoStorage = storage,
        idGenerator = FakeIdGenerator(),
        deviceIdProvider = FakeDeviceIdProvider(THIS_INSTALL),
        clock = FakeClock(NOW),
        analytics = RecordingAnalytics(),
    )

    private val tally: Encounter = encounterFixture(
        ID,
        OCCURRED,
        locationSource = LocationSource.CURRENT_FIX,
        lat = 41.39864,
        lon = 2.17842,
    ).copy(coat = CatCoat.GINGER, deviceId = THIS_INSTALL)

    private suspend fun stored(id: String = ID): Encounter = encounters.loadEvery().first { it.id == id }

    private suspend fun storedPhoto(id: String = ID): EncounterPhoto = stored(id).photos.single()

    @Test
    fun `a cat without a photo gets the copy, thumbnail and digest, and keeps everything else`() = runTest {
        encounters.insert(tally)

        assertEquals(AttachResult.Attached, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(
            tally.copy(
                photos = listOf(
                    EncounterPhoto(
                        id = "id-1",
                        encounterId = ID,
                        photoPath = FakeImageResizer.PHOTO_PATH,
                        thumbPath = FakeImageResizer.THUMB_PATH,
                        galleryUri = null,
                        sourceMediaUri = PHONE_ITEM,
                        sourceDigest = FakeDigest.SHA,
                        deviceId = THIS_INSTALL,
                        addedAt = NOW,
                    ),
                ),
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
        assertEquals("id-1", storedPhoto().id)
    }

    @Test
    fun `a camera original goes to the gallery when the setting is on`() = runTest {
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.CAMERA)

        assertEquals(1, gallery.calls)
        assertEquals(FakeGallerySaver.URI, storedPhoto().galleryUri)
    }

    @Test
    fun `a camera original stays out of the gallery when the setting is off`() = runTest {
        settings.saveOriginals = false
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.CAMERA)

        assertEquals(0, gallery.calls)
        assertEquals(null, storedPhoto().galleryUri)
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
    fun `a cat that has a photo gets another after it and keeps the first`() = runTest {
        val photographed = tally.withPhoto(photoPath = "own.jpg", thumbPath = "own_thumb.jpg", sourceDigest = "own")
        encounters.insert(photographed)

        assertEquals(AttachResult.Attached, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        val added = EncounterPhoto(
            id = "id-1",
            encounterId = ID,
            photoPath = FakeImageResizer.PHOTO_PATH,
            thumbPath = FakeImageResizer.THUMB_PATH,
            galleryUri = null,
            sourceMediaUri = PHONE_ITEM,
            sourceDigest = FakeDigest.SHA,
            deviceId = THIS_INSTALL,
            addedAt = NOW,
        )
        assertEquals(photographed.copy(photos = photographed.photos + added, updatedAt = NOW), stored())
    }

    @Test
    fun `a photo this cat already has is not added again and costs no disk`() = runTest {
        val photographed = tally.withPhoto(sourceDigest = FakeDigest.SHA)
        encounters.insert(photographed)

        assertEquals(AttachResult.AlreadyThere, attachPhoto(ID, SOURCE, PhotoSource.CAMERA))

        assertEquals(photographed, stored())
        assertEquals(0, resizer.calls)
        assertEquals(0, gallery.calls)
    }

    @Test
    fun `a photo whose digest cannot be read is never taken for one the cat already has`() = runTest {
        digest.result = null
        encounters.insert(tally.withPhoto(photoPath = "own.jpg", sourceDigest = null))

        assertEquals(AttachResult.Attached, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(2, stored().photos.size)
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
    fun `a lost attempt without a thumbnail removes only its copy`() = runTest {
        encounters.insert(tally)
        resizer.result = StoredPhoto(photoPath = FakeImageResizer.PHOTO_PATH, thumbPath = null)
        resizer.duringStore = { encounters.softDelete(ID, NOW) }

        assertEquals(AttachResult.NotAttachable, attachPhoto(ID, SOURCE, PhotoSource.GALLERY))

        assertEquals(listOf(FakeImageResizer.PHOTO_PATH), storage.deleted)
    }

    @Test
    fun `a write that fails removes the files it had written`() = runTest {
        encounters.insert(tally)
        encounters.addPhotoShouldThrow = IllegalStateException("disk full")

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
        assertEquals(listOf("id-1", "id-2"), resizer.baseNames)
        assertEquals(FakeDigest.SHA, storedPhoto().sourceDigest)
        assertEquals(FakeDigest.SHA, storedPhoto("cat-2").sourceDigest)
    }

    @Test
    fun `a cancellation after the write has landed keeps the files the cat now points at`() = runTest {
        encounters.insert(tally)
        lateinit var job: Job
        encounters.afterAddPhoto = {
            job.cancel()
            yield()
        }

        job = launch { attachPhoto(ID, SOURCE, PhotoSource.GALLERY) }
        job.join()

        assertTrue(storage.deleted.isEmpty())
        assertEquals(FakeImageResizer.PHOTO_PATH, storedPhoto().photoPath)
    }

    @Test
    fun `a cancellation while the original goes to the gallery removes the copies`() = runTest {
        encounters.insert(tally)
        lateinit var job: Job
        gallery.duringSave = {
            job.cancel()
            yield()
        }

        job = launch { attachPhoto(ID, SOURCE, PhotoSource.CAMERA) }
        job.join()

        assertEquals(listOf(FakeImageResizer.PHOTO_PATH, FakeImageResizer.THUMB_PATH), storage.deleted)
        assertEquals(tally, stored())
    }

    @Test
    fun `a photo chosen from the gallery remembers the item it came from`() = runTest {
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.GALLERY)

        assertEquals(PHONE_ITEM, storedPhoto().sourceMediaUri)
    }

    @Test
    fun `a photo from the camera is never linked to a picked item`() = runTest {
        encounters.insert(tally)

        attachPhoto(ID, SOURCE, PhotoSource.CAMERA)

        assertEquals(null, storedPhoto().sourceMediaUri)
        assertEquals(emptyList(), locator.asked)
    }

    @Test
    fun `a photo given to a cat another install logged keeps its picked item and names this install`() = runTest {
        encounters.insert(tally.copy(deviceId = "another-install"))

        attachPhoto(ID, SOURCE, PhotoSource.GALLERY)

        assertEquals(PHONE_ITEM to THIS_INSTALL, storedPhoto().sourceMediaUri to storedPhoto().deviceId)
    }

    @Test
    fun `a photo given to a cat another install logged opens its original here and not on that install`() = runTest {
        encounters.insert(tally.copy(deviceId = "another-install"))

        attachPhoto(ID, SOURCE, PhotoSource.CAMERA)

        assertEquals(FakeGallerySaver.URI, storedPhoto().galleryUri)
        assertEquals(GalleryLink(FakeGallerySaver.URI, ownedByApp = true), storedPhoto().galleryLink(THIS_INSTALL))
        assertEquals(null, storedPhoto().galleryLink("another-install"))
    }

    private companion object {
        const val ID = "cat-1"
        const val THIS_INSTALL = "install-1"
        const val PHONE_ITEM = "content://media/external/images/media/18"
        const val SOURCE = "content://picker/1"
        val OCCURRED = Instant.parse("2026-09-21T10:00:00Z")
        val NOW = Instant.parse("2026-09-23T12:00:00Z")
    }
}
