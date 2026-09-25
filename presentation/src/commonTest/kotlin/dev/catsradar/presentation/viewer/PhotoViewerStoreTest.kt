package dev.catsradar.presentation.viewer

import app.cash.turbine.test
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.ResolveGalleryLink
import dev.catsradar.presentation.counter.FakeDeviceIdProvider
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import dev.catsradar.presentation.encounters.withPhoto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
            assertEquals(PhotoViewerState.Showing(photoPath = "/data/photos/cat-1.jpg"), store.state.value)
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
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked)
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
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked)
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
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked)
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked)
            runCurrent()
            galleryItems.gate?.complete(Unit)
            runCurrent()
            assertEquals(PhotoViewerEffect.OpenInGallery(uri = SAVED), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a tap with nothing offered opens nothing and never asks the gallery`() = runTest(mainDispatcher) {
        repository.insert(photographedCat())
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.OpenInGalleryClicked)
            runCurrent()
            expectNoEvents()
        }
        assertEquals(emptyList(), galleryItems.asked)
    }

    private fun photographedCat(galleryUri: String? = null) = encounterFixture(ID, OCCURRED)
        .copy(kind = EncounterKind.PHOTO)
        .withPhoto(photoPath = "cat-1.jpg", galleryUri = galleryUri)

    private fun newStore() = PhotoViewerStore(
        encounterId = ID,
        observeEncounter = ObserveEncounter(repository),
        resolveGalleryLink = ResolveGalleryLink(galleryItems, FakeDeviceIdProvider(INSTALL)),
        stateMapper = PhotoViewerStateMapper(FakePhotoStorage(root = "/data/photos"), FakeDeviceIdProvider(INSTALL)),
    )

    private companion object {
        const val ID = "cat-1"
        const val INSTALL = "device-1"
        const val SAVED = "content://media/external/images/media/42"
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
