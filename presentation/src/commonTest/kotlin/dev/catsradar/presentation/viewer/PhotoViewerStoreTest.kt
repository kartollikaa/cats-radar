package dev.catsradar.presentation.viewer

import app.cash.turbine.test
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
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

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a cat with a photo shows its copy`() = runTest(mainDispatcher) {
        repository.insert(photographedCat())
        val store = newStore()
        runCurrent()

        assertEquals(PhotoViewerState.Showing(photoPath = "/data/photos/cat-1.jpg"), store.state.value)
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

    private fun photographedCat() =
        encounterFixture(ID, OCCURRED).copy(kind = EncounterKind.PHOTO, photoPath = "cat-1.jpg")

    private fun newStore() = PhotoViewerStore(
        encounterId = ID,
        observeEncounter = ObserveEncounter(repository),
        stateMapper = PhotoViewerStateMapper(FakePhotoStorage(root = "/data/photos")),
    )

    private companion object {
        const val ID = "cat-1"
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
