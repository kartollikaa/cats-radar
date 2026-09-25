package dev.catsradar.presentation.detail

import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.GalleryItemLocator
import dev.catsradar.domain.usecase.AttachPhoto
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.presentation.NoAnalytics
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeDeviceIdProvider
import dev.catsradar.presentation.counter.FakeDigest
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.counter.FakeGallerySaver
import dev.catsradar.presentation.counter.FakeIdGenerator
import dev.catsradar.presentation.counter.FakeImageResizer
import dev.catsradar.presentation.counter.FakeSettingsRepository
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EncounterDetailStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()
    private val clock = FakeClock(NOW)
    private val resizer = FakeImageResizer()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an existing encounter renders as loaded with its fields`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED, locationSource = LocationSource.NONE))
        val store = newStore()
        runCurrent()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(OCCURRED.toString(), state.timeLabel)
    }

    @Test
    fun `an id nobody has ever seen renders as missing, without throwing`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()

        assertEquals(EncounterDetailState.Missing, store.state.value)
    }

    @Test
    fun `an encounter soft-deleted elsewhere is never presented as live`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED, deletedAt = NOW))
        val store = newStore()
        runCurrent()

        assertEquals(EncounterDetailState.Missing, store.state.value)
    }

    @Test
    fun `an encounter deleted elsewhere while on screen turns the screen to missing`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        assertIs<EncounterDetailState.Loaded>(store.state.value)

        repository.softDelete(ID, NOW)
        runCurrent()

        assertEquals(EncounterDetailState.Missing, store.state.value)
    }

    @Test
    fun `delete soft-deletes once and shows the undo affordance`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        assertEquals(listOf(ID), repository.softDeletedIds)
        assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)
        assertEquals(null, repository.observeById(ID).value())
    }

    @Test
    fun `pressing delete twice soft-deletes exactly once`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.DeleteClicked)
        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        assertEquals(listOf(ID), repository.softDeletedIds)
    }

    @Test
    fun `undo within the window clears the deletion and the encounter is live again`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.seconds)
        store.dispatch(EncounterDetailIntent.UndoClicked)
        runCurrent()

        assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(null, repository.observeById(ID).value()?.deletedAt)
        assertEquals(1, repository.observeAll().value().size)
    }

    @Test
    fun `the undo affordance disappears when the window closes and the screen navigates back once`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            val store = newStore()
            runCurrent()

            store.effects.test {
                store.dispatch(EncounterDetailIntent.DeleteClicked)
                runCurrent()
                assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

                advanceTimeBy(Tuning.UNDO_VISIBLE - 1.milliseconds)
                runCurrent()
                assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

                advanceTimeBy(1.milliseconds)
                runCurrent()
                assertEquals(EncounterDetailState.Deleted(undoVisible = false), store.state.value)
                assertEquals(EncounterDetailEffect.NavigateBack, awaitItem())

                advanceTimeBy(Tuning.UNDO_VISIBLE * 3)
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun `undo after the window closed is a no-op and the deletion stands`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE + 1.milliseconds)
        runCurrent()

        store.dispatch(EncounterDetailIntent.UndoClicked)
        runCurrent()

        assertEquals(EncounterDetailState.Deleted(undoVisible = false), store.state.value)
        assertEquals(null, repository.observeById(ID).value())
    }

    @Test
    fun `delete on a missing encounter does nothing`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        assertEquals(EncounterDetailState.Missing, store.state.value)
        assertFalse(ID in repository.softDeletedIds)
    }

    @Test
    fun `a failed delete restores the loaded state instead of showing a deletion that did not happen`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            repository.softDeleteShouldThrow = IllegalStateException("disk full")
            val store = newStore()
            runCurrent()

            store.dispatch(EncounterDetailIntent.DeleteClicked)
            runCurrent()

            assertIs<EncounterDetailState.Loaded>(store.state.value)
            store.effects.test {
                advanceTimeBy(Tuning.UNDO_VISIBLE * 2)
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun `take a photo opens the camera and choose from gallery opens the picker`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked)
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera, awaitItem())
            store.dispatch(EncounterDetailIntent.PhotoTaken(null))
            store.dispatch(EncounterDetailIntent.PickPhotoClicked)
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhotoPicker, awaitItem())
        }
    }

    @Test
    fun `a cat that already has a photo opens neither`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).copy(photoPath = "own.jpg"))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked)
            store.dispatch(EncounterDetailIntent.PickPhotoClicked)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `a second tap before the camera answers opens nothing`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked)
            store.dispatch(EncounterDetailIntent.TakePhotoClicked)
            store.dispatch(EncounterDetailIntent.PickPhotoClicked)
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera, awaitItem())
            expectNoEvents()

            store.dispatch(EncounterDetailIntent.PhotoTaken(null))
            store.dispatch(EncounterDetailIntent.TakePhotoClicked)
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera, awaitItem())
        }
    }

    @Test
    fun `a photo from the camera lands on the cat and its original is discarded`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoTaken(CAPTURE))
            runCurrent()
            assertEquals(EncounterDetailEffect.DiscardCapture(CAPTURE), awaitItem())
        }
        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals("/data/photos/cat.jpg", state.photoPath)
        assertEquals(null, state.addPhoto)
    }

    @Test
    fun `a photo from the gallery lands on the cat and nothing is discarded`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoPicked(PICKED))
            runCurrent()
            expectNoEvents()
        }
        assertEquals("/data/photos/cat.jpg", assertIs<EncounterDetailState.Loaded>(store.state.value).photoPath)
    }

    @Test
    fun `a cancelled camera or picker changes nothing`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        val before = store.state.value

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoTaken(null))
            store.dispatch(EncounterDetailIntent.PhotoPicked(null))
            runCurrent()
            expectNoEvents()
        }
        assertEquals(before, store.state.value)
    }

    @Test
    fun `the offer shows the photo being attached until it lands`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotoPicked(PICKED))
        runCurrent()
        assertEquals(AddPhoto.ATTACHING, assertIs<EncounterDetailState.Loaded>(store.state.value).addPhoto)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(null, assertIs<EncounterDetailState.Loaded>(store.state.value).addPhoto)
    }

    @Test
    fun `a successful attach stays in progress until the photo arrives, never offering again`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            val store = newStore()
            runCurrent()
            repository.observeDelay = 5.seconds

            store.dispatch(EncounterDetailIntent.PhotoPicked(PICKED))
            runCurrent()
            assertEquals(AddPhoto.ATTACHING, assertIs<EncounterDetailState.Loaded>(store.state.value).addPhoto)

            advanceTimeBy(6.seconds)
            runCurrent()
            val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
            assertEquals(null, state.addPhoto)
            assertEquals("/data/photos/cat.jpg", state.photoPath)
        }

    @Test
    fun `taking a photo while one is being attached opens nothing`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.PhotoPicked(PICKED))
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked)
            store.dispatch(EncounterDetailIntent.PickPhotoClicked)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `deleting while a photo is being attached leaves the removed state, and undo brings the offer back`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            resizer.storeDelay = 1.seconds
            val store = newStore()
            runCurrent()

            store.dispatch(EncounterDetailIntent.PhotoPicked(PICKED))
            runCurrent()
            store.dispatch(EncounterDetailIntent.DeleteClicked)
            runCurrent()
            assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

            store.dispatch(EncounterDetailIntent.UndoClicked)
            runCurrent()
            val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
            assertEquals(AddPhoto.READY, state.addPhoto)
            assertEquals(null, state.photoPath)
        }

    @Test
    fun `an unreadable photo says so and the offer comes back`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        resizer.result = null
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoTaken(CAPTURE))
            runCurrent()
            assertEquals(EncounterDetailEffect.PhotoNotAttached, awaitItem())
            assertEquals(EncounterDetailEffect.DiscardCapture(CAPTURE), awaitItem())
        }
        assertEquals(AddPhoto.READY, assertIs<EncounterDetailState.Loaded>(store.state.value).addPhoto)
    }

    @Test
    fun `a failed write says the photo was not attached`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.attachPhotoShouldThrow = IllegalStateException("disk full")
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoPicked(PICKED))
            runCurrent()
            assertEquals(EncounterDetailEffect.PhotoNotAttached, awaitItem())
        }
        assertEquals(AddPhoto.READY, assertIs<EncounterDetailState.Loaded>(store.state.value).addPhoto)
    }

    @Test
    fun `a tap on the photo opens the viewer`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).copy(kind = EncounterKind.PHOTO, photoPath = "cat-1.jpg"))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoClicked)
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhoto, awaitItem())
        }
    }

    @Test
    fun `a cat without a photo has no viewer to open`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoClicked)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `a tap on the coordinates of a cat on the map opens the map`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).copy(lat = 41.39, lon = 2.17))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.CoordinatesClicked)
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenMap, awaitItem())
        }
    }

    @Test
    fun `a cat that is not on the map opens no map`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).copy(lat = 123.4, lon = 2.17))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.CoordinatesClicked)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `a removed cat opens no map`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).copy(lat = 41.39, lon = 2.17))
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.CoordinatesClicked)
            runCurrent()
            expectNoEvents()
        }
    }

    private fun TestScope.newStore(): EncounterDetailStore = EncounterDetailStore(
        encounterId = ID,
        observeEncounter = ObserveEncounter(repository),
        deleteEncounter = DeleteEncounter(repository, clock, analytics = NoAnalytics),
        undoDelete = UndoDelete(repository, analytics = NoAnalytics),
        setCoat = SetCoat(repository, clock, analytics = NoAnalytics),
        attachPhoto = AttachPhoto(
            encounterRepository = repository,
            settingsRepository = FakeSettingsRepository(),
            imageResizer = resizer,
            digest = FakeDigest(),
            gallerySaver = FakeGallerySaver(),
            galleryItemLocator = LocatesNoGalleryItem,
            photoStorage = FakePhotoStorage(),
            idGenerator = FakeIdGenerator(),
            deviceIdProvider = FakeDeviceIdProvider(),
            clock = clock,
            analytics = NoAnalytics,
        ),
        stateMapper = EncounterDetailStateMapper(FakeDateTimeFormatter(), FakePhotoStorage()),
        clock = clock,
        timeZone = TimeZone.UTC,
    )

    private suspend fun <T> Flow<T>.value(): T = first()

    private companion object {
        const val ID = "cat-1"
        const val CAPTURE = "content://captures/1"
        const val PICKED = "content://picker/1"
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}

private object LocatesNoGalleryItem : GalleryItemLocator {
    override suspend fun locate(pickedUri: String): String? = null
}
