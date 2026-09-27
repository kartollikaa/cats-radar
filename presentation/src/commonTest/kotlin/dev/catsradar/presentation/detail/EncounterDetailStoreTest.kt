package dev.catsradar.presentation.detail

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.platform.GalleryItemLocator
import dev.catsradar.domain.usecase.AttachPhoto
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.ObserveEncounterPlace
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.presentation.NoAnalytics
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeDeviceIdProvider
import dev.catsradar.presentation.counter.FakeDigest
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.counter.FakeGallerySaver
import dev.catsradar.presentation.counter.FakeIdGenerator
import dev.catsradar.presentation.counter.FakeImageResizer
import dev.catsradar.presentation.counter.FakePlaceCellRepository
import dev.catsradar.presentation.counter.FakeSettingsRepository
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import dev.catsradar.presentation.encounters.withPhoto
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EncounterDetailStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()
    private val cells = FakePlaceCellRepository()
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

        val state = store.shownPage()
        assertEquals(OCCURRED.toString(), state.timeLabel)
    }

    @Test
    fun `the pages are the opened cat's outing, newest first, with the opened cat on screen`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
            repository.insert(encounterFixture(THIRD, OCCURRED - 1.days))
            val store = newStore()
            runCurrent()

            val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
            assertEquals(listOf(OTHER, ID), state.pages.map { it.id })
            assertEquals(ID, state.currentId)
            assertEquals(2, state.currentNumber)
        }

    @Test
    fun `the screen starts on the restored cat while it is live`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore(restoredId = OTHER)
        runCurrent()

        assertEquals(OTHER, store.shownPage().id)
    }

    @Test
    fun `a restored cat that is gone starts the screen on the opened one`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes, deletedAt = NOW))
        val store = newStore(restoredId = OTHER)
        runCurrent()

        assertEquals(ID, store.shownPage().id)
    }

    @Test
    fun `a cat logged into the outing joins the pages and the screen stays on its cat`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        runCurrent()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(OTHER, ID), state.pages.map { it.id })
        assertEquals(ID, state.currentId)
    }

    @Test
    fun `a cat deleted elsewhere hands the screen to the next older cat`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(THIRD, OCCURRED - 10.minutes))
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore()
        runCurrent()

        repository.softDelete(ID, NOW)
        runCurrent()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(OTHER, THIRD), state.pages.map { it.id })
        assertEquals(THIRD, state.currentId)
    }

    @Test
    fun `the oldest cat deleted elsewhere hands the screen to the newer one`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore()
        runCurrent()

        repository.softDelete(ID, NOW)
        runCurrent()

        assertEquals(OTHER, store.shownPage().id)
    }

    @Test
    fun `a cat deleted elsewhere and brought back returns the screen to it`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        repository.softDelete(ID, NOW)
        runCurrent()
        assertEquals(EncounterDetailState.Missing, store.state.value)

        repository.undoDelete(ID)
        runCurrent()

        assertEquals(ID, store.shownPage().id)
    }

    @Test
    fun `delete removes the cat on screen, and undo shows it again`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore(restoredId = OTHER)
        runCurrent()

        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()
        assertEquals(listOf(OTHER), repository.softDeletedIds)
        assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

        store.dispatch(EncounterDetailIntent.UndoClicked)
        runCurrent()
        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(OTHER, ID), state.pages.map { it.id })
        assertEquals(OTHER, state.currentId)
    }

    @Test
    fun `settling on another page puts that cat on screen`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PageSettled(OTHER))
        runCurrent()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(OTHER, state.currentId)
        assertEquals(1, state.currentNumber)
    }

    @Test
    fun `a settled id off the pages is ignored`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED - 1.days))
        val store = newStore()
        runCurrent()
        val before = store.state.value

        store.dispatch(EncounterDetailIntent.PageSettled(OTHER))
        store.dispatch(EncounterDetailIntent.PageSettled("elsewhere"))
        runCurrent()

        assertEquals(before, store.state.value)
    }

    @Test
    fun `after settling, a delete removes the settled cat`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PageSettled(OTHER))
        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        assertEquals(listOf(OTHER), repository.softDeletedIds)
    }

    @Test
    fun `the removed state holds while another cat of the outing changes mid-delete`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        repository.softDeleteDelay = 1.seconds
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()
        store.dispatch(EncounterDetailIntent.CoatPicked(OTHER, CoatOption.GINGER))
        runCurrent()
        assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)
        assertEquals(listOf(ID), repository.softDeletedIds)
    }

    @Test
    fun `each page shows its own cat's place`() = runTest(mainDispatcher) {
        repository.insert(
            encounterFixture(ID, OCCURRED, locationSource = LocationSource.CURRENT_FIX)
                .copy(lat = 41.39, lon = 2.17, placeCellId = "sp3e3q"),
        )
        repository.insert(
            encounterFixture(OTHER, OCCURRED + 10.minutes, locationSource = LocationSource.CURRENT_FIX)
                .copy(lat = 38.72, lon = -9.14, placeCellId = "eycs0p"),
        )
        cells.upsert(namedCell("sp3e3q"))
        cells.upsert(namedCell("eycs0p", countryCode = "PT", countryName = "Portugal", locality = "Lisbon"))
        val store = newStore()
        runCurrent()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf("Lisbon", "Barcelona"), state.pages.map { it.place?.title })
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
    fun `back navigates back once, however often it is tapped`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.BackClicked)
            store.dispatch(EncounterDetailIntent.BackClicked)
            runCurrent()

            assertEquals(EncounterDetailEffect.NavigateBack, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `back during the undo window navigates back once, and the window closing adds nothing`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            val store = newStore()
            runCurrent()

            store.effects.test {
                store.dispatch(EncounterDetailIntent.DeleteClicked)
                runCurrent()
                store.dispatch(EncounterDetailIntent.BackClicked)
                runCurrent()
                assertEquals(EncounterDetailEffect.NavigateBack, awaitItem())

                advanceTimeBy(Tuning.UNDO_VISIBLE * 2)
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun `back from a cat nobody has seen still navigates back`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.BackClicked)
            runCurrent()

            assertEquals(EncounterDetailState.Missing, store.state.value)
            assertEquals(EncounterDetailEffect.NavigateBack, awaitItem())
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
    fun `a coat lands on the cat it was picked for`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.CoatPicked(OTHER, CoatOption.GINGER))
        runCurrent()

        assertEquals(CoatOption.GINGER, repository.observeById(OTHER).value()?.coat?.toOption())
        assertNull(assertNotNull(repository.observeById(ID).value()).coat)
    }

    @Test
    fun `the cat's place reaches the screen once its cell is named`() = runTest(mainDispatcher) {
        val located = encounterFixture(ID, OCCURRED, locationSource = LocationSource.CURRENT_FIX)
            .copy(lat = 41.39, lon = 2.17, placeCellId = "sp3e3q")
        repository.insert(located)
        val store = newStore()
        runCurrent()
        assertEquals(null, store.shownPage().place)

        cells.upsert(namedCell("sp3e3q"))
        runCurrent()

        assertEquals(
            DetailPlace(title = "Barcelona", country = "Spain", flag = "🇪🇸"),
            store.shownPage().place,
        )
    }

    private fun TestScope.newStore(restoredId: String? = null): EncounterDetailStore = EncounterDetailStore(
        openedId = ID,
        restoredId = restoredId,
        observeEncounters = ObserveEncounters(repository),
        observeEncounterPlace = ObserveEncounterPlace(cells),
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

    private fun namedCell(
        cellId: String,
        countryCode: String = "ES",
        countryName: String = "Spain",
        locality: String = "Barcelona",
    ) = PlaceCell(
        cellId = cellId,
        centerLat = 41.39,
        centerLon = 2.17,
        countryCode = countryCode,
        countryName = countryName,
        adminArea = null,
        locality = locality,
        subLocality = null,
        status = PlaceStatus.RESOLVED,
        attempts = 1,
        lastAttemptAt = NOW,
        resolvedAt = NOW,
    )

    private suspend fun <T> Flow<T>.value(): T = first()

    private companion object {
        const val ID = "cat-1"
        const val OTHER = "cat-2"
        const val THIRD = "cat-3"
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EncounterDetailStorePhotoTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()
    private val cells = FakePlaceCellRepository()
    private val clock = FakeClock(NOW)
    private val resizer = FakeImageResizer()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `take a photo opens the camera and choose from gallery opens the picker`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera(ID), awaitItem())
            store.dispatch(EncounterDetailIntent.PhotoTaken(ID, null))
            store.dispatch(EncounterDetailIntent.PickPhotoClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhotoPicker(ID), awaitItem())
        }
    }

    @Test
    fun `a cat that already has a photo can still be given another`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).withPhoto(photoPath = "own.jpg"))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera(ID), awaitItem())
        }
    }

    @Test
    fun `a photo taken of a cat that has one is added after it`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).withPhoto(photoPath = "own.jpg"))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotoTaken(ID, CAPTURE))
        runCurrent()

        val state = store.shownPage()
        assertEquals(listOf("/data/photos/own.jpg", "/data/photos/cat.jpg"), state.photos.map { it.path })
        assertEquals(AddPhoto.READY, state.addPhoto)
    }

    @Test
    fun `a photo picked for a cat that has one is added after it`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).withPhoto(photoPath = "own.jpg"))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotosPicked(ID, listOf(PICKED)))
        runCurrent()

        val state = store.shownPage()
        assertEquals(listOf("/data/photos/own.jpg", "/data/photos/cat.jpg"), state.photos.map { it.path })
    }

    @Test
    fun `leaving mid-attempt leaves the cat as the attempt found it`() = runTest(mainDispatcher) {
        val tally = encounterFixture(ID, OCCURRED)
        repository.insert(tally)
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.PhotosPicked(ID, listOf(PICKED)))
        runCurrent()

        ViewModelStore().apply { put("detail", store) }.clear()
        advanceTimeBy(2.seconds)
        runCurrent()

        assertEquals(listOf(tally), repository.encounters())
    }

    @Test
    fun `a tap on a cat's second photo opens the viewer on that photo`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).withPhoto(photoPath = "own.jpg"))
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.PhotosPicked(ID, listOf(PICKED)))
        runCurrent()
        val second = store.shownPage().photos.last().id

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoClicked(ID, second))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhoto(ID, second), awaitItem())
        }
    }

    @Test
    fun `a second tap before the camera answers opens nothing`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
            store.dispatch(EncounterDetailIntent.PickPhotoClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera(ID), awaitItem())
            expectNoEvents()

            store.dispatch(EncounterDetailIntent.PhotoTaken(ID, null))
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera(ID), awaitItem())
        }
    }

    @Test
    fun `a photo from the camera lands on the cat and its original is discarded`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoTaken(ID, CAPTURE))
            runCurrent()
            assertEquals(EncounterDetailEffect.DiscardCapture(CAPTURE), awaitItem())
        }
        val state = store.shownPage()
        assertEquals(listOf("/data/photos/cat.jpg"), state.photos.map { it.path })
        assertEquals(AddPhoto.READY, state.addPhoto)
    }

    @Test
    fun `a photo from the gallery lands on the cat and nothing is discarded`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotosPicked(ID, listOf(PICKED)))
            runCurrent()
            expectNoEvents()
        }
        val state = store.shownPage()
        assertEquals(listOf("/data/photos/cat.jpg"), state.photos.map { it.path })
    }

    @Test
    fun `a cancelled camera or picker changes nothing`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        val before = store.state.value

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoTaken(ID, null))
            store.dispatch(EncounterDetailIntent.PhotosPicked(ID, emptyList()))
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

        store.dispatch(EncounterDetailIntent.PhotosPicked(ID, listOf(PICKED)))
        runCurrent()
        assertEquals(AddPhoto.ATTACHING, store.shownPage().addPhoto)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(AddPhoto.READY, store.shownPage().addPhoto)
    }

    @Test
    fun `a successful attach stays in progress until the photo arrives, never offering again`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            val store = newStore()
            runCurrent()
            repository.observeDelay = 5.seconds

            store.dispatch(EncounterDetailIntent.PhotosPicked(ID, listOf(PICKED)))
            runCurrent()
            assertEquals(AddPhoto.ATTACHING, store.shownPage().addPhoto)

            advanceTimeBy(4.seconds)
            runCurrent()
            assertEquals(AddPhoto.ATTACHING, store.shownPage().addPhoto)

            advanceTimeBy(2.seconds)
            runCurrent()
            val state = store.shownPage()
            assertEquals(AddPhoto.READY, state.addPhoto)
            assertEquals(listOf("/data/photos/cat.jpg"), state.photos.map { it.path })
        }

    @Test
    fun `taking a photo while one is being attached opens nothing`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.PhotosPicked(ID, listOf(PICKED)))
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
            store.dispatch(EncounterDetailIntent.PickPhotoClicked(ID))
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

            store.dispatch(EncounterDetailIntent.PhotosPicked(ID, listOf(PICKED)))
            runCurrent()
            store.dispatch(EncounterDetailIntent.DeleteClicked)
            runCurrent()
            assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

            store.dispatch(EncounterDetailIntent.UndoClicked)
            runCurrent()
            val state = store.shownPage()
            assertEquals(AddPhoto.READY, state.addPhoto)
            assertEquals(emptyList(), state.photos)
        }

    @Test
    fun `an unreadable photo says so and the offer comes back`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        resizer.result = null
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoTaken(ID, CAPTURE))
            runCurrent()
            assertEquals(EncounterDetailEffect.PhotoNotAttached, awaitItem())
            assertEquals(EncounterDetailEffect.DiscardCapture(CAPTURE), awaitItem())
        }
        assertEquals(AddPhoto.READY, store.shownPage().addPhoto)
    }

    @Test
    fun `a picked photo the cat already has is not added again, and the screen says so`() = runTest(mainDispatcher) {
        val photographed = encounterFixture(ID, OCCURRED).withPhoto(photoPath = "own.jpg")
            .let { cat -> cat.copy(photos = cat.photos.map { it.copy(sourceDigest = "digest") }) }
        repository.insert(photographed)
        val store = newStore()
        runCurrent()
        val before = store.state.value

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotosPicked(ID, listOf(PICKED)))
            runCurrent()
            assertEquals(EncounterDetailEffect.PhotoAlreadyThere, awaitItem())
        }
        assertEquals(before, store.state.value)
        assertEquals(listOf(photographed), repository.encounters())
    }

    @Test
    fun `a failed write says the photo was not attached`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.addPhotoShouldThrow = IllegalStateException("disk full")
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotosPicked(ID, listOf(PICKED)))
            runCurrent()
            assertEquals(EncounterDetailEffect.PhotoNotAttached, awaitItem())
        }
        assertEquals(AddPhoto.READY, store.shownPage().addPhoto)
    }

    @Test
    fun `a tap on the photo opens the viewer`() = runTest(mainDispatcher) {
        repository.insert(
            encounterFixture(ID, OCCURRED).copy(kind = EncounterKind.PHOTO).withPhoto(photoPath = "cat-1.jpg")
        )
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoClicked(ID, ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhoto(ID, ID), awaitItem())
        }
    }

    @Test
    fun `a cat without a photo has no viewer to open`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoClicked(ID, "anything"))
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
            store.dispatch(EncounterDetailIntent.CoordinatesClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenMap(ID), awaitItem())
        }
    }

    @Test
    fun `a cat that is not on the map opens no map`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).copy(lat = 123.4, lon = 2.17))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.CoordinatesClicked(ID))
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
            store.dispatch(EncounterDetailIntent.CoordinatesClicked(ID))
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `set on map opens the picker for a cat with no location`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.SetLocationClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenLocationPicker(ID), awaitItem())
        }
    }

    @Test
    fun `a cat with a location opens no picker`() = runTest(mainDispatcher) {
        val located = encounterFixture(ID, OCCURRED, locationSource = LocationSource.EXIF).copy(lat = 41.39, lon = 2.17)
        repository.insert(located)
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.SetLocationClicked(ID))
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `the camera and the picker open for the cat they were asked for`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(OTHER))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera(OTHER), awaitItem())
            store.dispatch(EncounterDetailIntent.PhotoTaken(OTHER, uri = null))
            store.dispatch(EncounterDetailIntent.PickPhotoClicked(OTHER))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhotoPicker(OTHER), awaitItem())
        }
    }

    @Test
    fun `a photo lands on the cat its result names, not on the cat on screen`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
        store.dispatch(EncounterDetailIntent.PhotoTaken(OTHER, CAPTURE))
        runCurrent()

        assertEquals(1, repository.observeById(OTHER).value()?.photos?.size)
        assertEquals(0, repository.observeById(ID).value()?.photos?.size)
    }

    @Test
    fun `the viewer and the map open on the cat that was tapped`() = runTest(mainDispatcher) {
        repository.insert(
            encounterFixture(ID, OCCURRED).copy(lat = 41.39, lon = 2.17).withPhoto(photoPath = "cat-1.jpg")
        )
        val store = newStore()
        runCurrent()
        val photoId = store.shownPage().photos.first().id

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoClicked(ID, photoId))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhoto(ID, photoId), awaitItem())
            store.dispatch(EncounterDetailIntent.CoordinatesClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenMap(ID), awaitItem())
        }
    }

    @Test
    fun `a tap naming a cat not on the pages opens neither the viewer nor the map`() =
        runTest(mainDispatcher) {
            repository.insert(
                encounterFixture(ID, OCCURRED).copy(lat = 41.39, lon = 2.17).withPhoto(photoPath = "cat-1.jpg")
            )
            repository.insert(
                encounterFixture(OTHER, OCCURRED - 1.days, locationSource = LocationSource.CURRENT_FIX)
                    .copy(lat = 41.39, lon = 2.17).withPhoto(photoPath = "cat-2.jpg"),
            )
            val store = newStore()
            runCurrent()

            store.effects.test {
                store.dispatch(EncounterDetailIntent.PhotoClicked(OTHER, OTHER))
                store.dispatch(EncounterDetailIntent.CoordinatesClicked(OTHER))
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun `set on map names the cat it was tapped for, and a cat not on the pages opens nothing`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            repository.insert(encounterFixture(OTHER, OCCURRED - 1.days))
            val store = newStore()
            runCurrent()

            store.effects.test {
                store.dispatch(EncounterDetailIntent.SetLocationClicked(ID))
                runCurrent()
                assertEquals(EncounterDetailEffect.OpenLocationPicker(ID), awaitItem())
                store.dispatch(EncounterDetailIntent.SetLocationClicked(OTHER))
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun `a tap on another page's photo, coordinates or set on map opens it for that cat`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            repository.insert(
                encounterFixture(OTHER, OCCURRED + 10.minutes, locationSource = LocationSource.CURRENT_FIX)
                    .copy(lat = 41.39, lon = 2.17).withPhoto(photoPath = "cat-2.jpg"),
            )
            repository.insert(encounterFixture(THIRD, OCCURRED + 20.minutes))
            val store = newStore()
            runCurrent()

            store.effects.test {
                store.dispatch(EncounterDetailIntent.PhotoClicked(OTHER, OTHER))
                runCurrent()
                assertEquals(EncounterDetailEffect.OpenPhoto(OTHER, OTHER), awaitItem())
                store.dispatch(EncounterDetailIntent.CoordinatesClicked(OTHER))
                runCurrent()
                assertEquals(EncounterDetailEffect.OpenMap(OTHER), awaitItem())
                store.dispatch(EncounterDetailIntent.SetLocationClicked(THIRD))
                runCurrent()
                assertEquals(EncounterDetailEffect.OpenLocationPicker(THIRD), awaitItem())
            }
        }

    @Test
    fun `one camera or picker at a time across the pages`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
            store.dispatch(EncounterDetailIntent.PickPhotoClicked(OTHER))
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(OTHER))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera(ID), awaitItem())
            expectNoEvents()

            store.dispatch(EncounterDetailIntent.PhotoTaken(ID, null))
            store.dispatch(EncounterDetailIntent.PickPhotoClicked(OTHER))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhotoPicker(OTHER), awaitItem())
        }
    }

    @Test
    fun `a photo attached to another page shows the attempt on that page alone`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotosPicked(OTHER, listOf(PICKED)))
        runCurrent()
        val attaching = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(AddPhoto.ATTACHING, AddPhoto.READY), attaching.pages.map { it.addPhoto })

        advanceTimeBy(2.seconds)
        runCurrent()
        val landed = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(AddPhoto.READY, AddPhoto.READY), landed.pages.map { it.addPhoto })
        assertEquals(listOf(1, 0), landed.pages.map { it.photos.size })
    }

    private fun TestScope.newStore(restoredId: String? = null): EncounterDetailStore = EncounterDetailStore(
        openedId = ID,
        restoredId = restoredId,
        observeEncounters = ObserveEncounters(repository),
        observeEncounterPlace = ObserveEncounterPlace(cells),
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
        const val OTHER = "cat-2"
        const val THIRD = "cat-3"
        const val CAPTURE = "content://captures/1"
        const val PICKED = "content://picker/1"
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}

private object LocatesNoGalleryItem : GalleryItemLocator {
    override suspend fun locate(pickedUri: String): String? = null
}
