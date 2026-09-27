package dev.catsradar.presentation.viewer

import app.cash.turbine.test
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.RemovePhoto
import dev.catsradar.domain.usecase.ResolveGalleryLink
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeDeviceIdProvider
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import dev.catsradar.presentation.encounters.withPhoto
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoViewerStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()
    private val galleryItems = FakeGalleryItems()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a cat with a photo shows its copy`() = runTest(mainDispatcher) {
        repository.insert(photographedCat())
        val store = newStore()

        store.effects.test {
            runCurrent()
            assertEquals(
                PhotoViewerState.Showing(
                    photos = persistentListOf(ViewerPhoto(id = ID, path = "/data/photos/cat-1.jpg")),
                    firstPage = 0,
                    timeLabel = "2026-09-22T10:00:00Z",
                    dayLabel = "2026-09-22",
                ),
                store.state.value,
            )
            expectNoEvents()
        }
    }

    @Test
    fun `a cat without a photo closes the viewer instead of showing black`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()

        store.effects.test {
            runCurrent()
            assertEquals(PhotoViewerEffect.Close, awaitItem())
            assertEquals(PhotoViewerState.Loading, store.state.value)
        }
    }

    @Test
    fun `an id nobody has seen closes the viewer`() = runTest(mainDispatcher) {
        val store = newStore()

        store.effects.test {
            runCurrent()
            assertEquals(PhotoViewerEffect.Close, awaitItem())
        }
    }

    @Test
    fun `a cat deleted while on screen closes the viewer, and back after it closes nothing more`() =
        runTest(mainDispatcher) {
            repository.insert(photographedCat())
            val store = newStore()
            runCurrent()

            store.effects.test {
                repository.softDelete(ID, OCCURRED)
                runCurrent()
                assertEquals(PhotoViewerEffect.Close, awaitItem())

                store.dispatch(PhotoViewerIntent.BackClicked)
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun `back closes the viewer once`() = runTest(mainDispatcher) {
        repository.insert(photographedCat())
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.BackClicked)
            store.dispatch(PhotoViewerIntent.BackClicked)
            runCurrent()
            assertEquals(PhotoViewerEffect.Close, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a cat whose original is still in the gallery opens it there`() = runTest(mainDispatcher) {
        repository.insert(photographedCat(galleryUri = SAVED))
        galleryItems.present += SAVED
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked(photoId = ID))
            runCurrent()
            assertEquals(PhotoViewerEffect.OpenInGallery(uri = SAVED), awaitItem())
        }
    }

    @Test
    fun `an original deleted from the gallery says so and opens nothing`() = runTest(mainDispatcher) {
        repository.insert(photographedCat(galleryUri = SAVED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked(photoId = ID))
            runCurrent()
            assertEquals(PhotoViewerEffect.GalleryItemGone, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a second tap while the first is being checked opens the gallery once`() = runTest(mainDispatcher) {
        repository.insert(photographedCat(galleryUri = SAVED))
        galleryItems.present += SAVED
        galleryItems.gate = CompletableDeferred()
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked(photoId = ID))
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked(photoId = ID))
            runCurrent()
            galleryItems.gate?.complete(Unit)
            runCurrent()
            assertEquals(PhotoViewerEffect.OpenInGallery(uri = SAVED), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `the gallery opens the original of the photo on screen, not the cover's`() = runTest(mainDispatcher) {
        val cat = photographedCat(galleryUri = SAVED)
        val second = cat.photos.single().copy(id = "second", galleryUri = SECOND_SAVED, addedAt = OCCURRED + 1.minutes)
        repository.insert(cat.copy(photos = cat.photos + second))
        galleryItems.present += listOf(SAVED, SECOND_SAVED)
        val store = newStore(openedOn = "second")
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked(photoId = "second"))
            runCurrent()
            assertEquals(PhotoViewerEffect.OpenInGallery(uri = SECOND_SAVED), awaitItem())
        }
        assertEquals(1, assertIs<PhotoViewerState.Showing>(store.state.value).firstPage)
    }

    @Test
    fun `a tap for a photo the cat no longer has opens nothing`() = runTest(mainDispatcher) {
        repository.insert(photographedCat(galleryUri = SAVED))
        galleryItems.present += SAVED
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked(photoId = "gone"))
            runCurrent()
            expectNoEvents()
        }
        assertEquals(emptyList(), galleryItems.asked)
    }

    @Test
    fun `a tap with nothing offered opens nothing and never asks the gallery`() = runTest(mainDispatcher) {
        repository.insert(photographedCat())
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked(photoId = ID))
            runCurrent()
            expectNoEvents()
        }
        assertEquals(emptyList(), galleryItems.asked)
    }

    @Test
    fun `remove remembers the displayed photo and cancel changes nothing`() = runTest(mainDispatcher) {
        repository.insert(photographedCat())
        val store = newStore()
        runCurrent()

        store.dispatch(PhotoViewerIntent.RemovePhotoClicked(ID))
        runCurrent()

        assertEquals(ID, assertIs<PhotoViewerState.Showing>(store.state.value).removingPhotoId)
        store.dispatch(PhotoViewerIntent.RemovePhotoCancelled)
        runCurrent()
        assertNull(assertIs<PhotoViewerState.Showing>(store.state.value).removingPhotoId)
        assertTrue(repository.removePhotoCalls.isEmpty())
    }

    @Test
    fun `confirm removes the requested displayed photo exactly once`() = runTest(mainDispatcher) {
        repository.insert(photographedCat())
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.RemovePhotoClicked(ID))
            store.dispatch(PhotoViewerIntent.RemovePhotoConfirmed)
            store.dispatch(PhotoViewerIntent.RemovePhotoConfirmed)
            runCurrent()

            assertEquals(listOf(Triple(ID, ID, OCCURRED)), repository.removePhotoCalls)
            assertEquals(PhotoViewerEffect.Close, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `removing one of several photos keeps the viewer on the nearest remaining photo`() =
        runTest(mainDispatcher) {
            val cat = photographedCat()
            val second = cat.photos.single().copy(id = SECOND, photoPath = "second.jpg", addedAt = OCCURRED + 1.minutes)
            repository.insert(cat.copy(photos = cat.photos + second))
            val store = newStore(openedOn = SECOND)
            runCurrent()

            store.effects.test {
                store.dispatch(PhotoViewerIntent.RemovePhotoClicked(SECOND))
                store.dispatch(PhotoViewerIntent.RemovePhotoConfirmed)
                runCurrent()

                val showing = assertIs<PhotoViewerState.Showing>(store.state.value)
                assertEquals(listOf(ID), showing.photos.map { it.id })
                assertEquals(0, showing.firstPage)
                assertFalse(showing.removalInFlight)
                assertNull(showing.removingPhotoId)
                expectNoEvents()
            }
        }

    @Test
    fun `a failed removal keeps the photo open and reports the failure once`() = runTest(mainDispatcher) {
        repository.insert(photographedCat())
        repository.removePhotoShouldThrow = IllegalStateException("disk")
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.RemovePhotoClicked(ID))
            store.dispatch(PhotoViewerIntent.RemovePhotoConfirmed)
            runCurrent()

            assertEquals(PhotoViewerEffect.RemovePhotoFailed, awaitItem())
            expectNoEvents()
            val showing = assertIs<PhotoViewerState.Showing>(store.state.value)
            assertEquals(listOf(ID), showing.photos.map { it.id })
            assertFalse(showing.removalInFlight)
            assertNull(showing.removingPhotoId)
        }
    }

    private fun photographedCat(galleryUri: String? = null) = encounterFixture(ID, OCCURRED)
        .copy(kind = EncounterKind.PHOTO)
        .withPhoto(photoPath = "cat-1.jpg", galleryUri = galleryUri)

    private fun newStore(openedOn: String? = null) = PhotoViewerStore(
        encounterId = ID,
        openedOn = openedOn,
        observeEncounter = ObserveEncounter(repository),
        removePhoto = RemovePhoto(repository, FakePhotoStorage(), FakeClock(OCCURRED)),
        resolveGalleryLink = ResolveGalleryLink(galleryItems, FakeDeviceIdProvider(INSTALL)),
        stateMapper = PhotoViewerStateMapper(
            FakeDateTimeFormatter(),
            FakePhotoStorage(root = "/data/photos"),
            FakeDeviceIdProvider(INSTALL),
        ),
        clock = FakeClock(OCCURRED),
        timeZone = TimeZone.UTC,
    )

    private companion object {
        const val ID = "cat-1"
        const val INSTALL = "device-1"
        const val SAVED = "content://media/external/images/media/42"
        const val SECOND_SAVED = "content://media/external/images/media/43"
        const val SECOND = "second"
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
