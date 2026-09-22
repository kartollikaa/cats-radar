package dev.catsradar.presentation.counter

import app.cash.turbine.Event
import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.platform.ExifData
import dev.catsradar.domain.usecase.LogPhoto
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.UndoImport
import dev.catsradar.domain.usecase.UndoLastTally
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CounterStoreTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Milestones are silenced by default so a test about undo or photos is not also a test about
    // celebrating the first cat; the milestone tests pass their own starting point.
    private val settings = FakeSettingsRepository(lastMilestone = Tuning.MILESTONES.last())
    private val exifReader = FakeExifReader()
    private val imageResizer = FakeImageResizer()
    private val gallerySaver = FakeGallerySaver()

    private fun milestonesIn(events: List<Event<CounterEffect>>): List<CounterEffect.MilestoneReached> =
        events.filterIsInstance<Event.Item<CounterEffect>>()
            .map { it.value }
            .filterIsInstance<CounterEffect.MilestoneReached>()

    private fun TestScope.newStore(
        encounterRepository: FakeEncounterRepository = FakeEncounterRepository(),
        locationPermissionRequestState: FakeLocationPermissionRequestState = FakeLocationPermissionRequestState(),
        settingsRepository: FakeSettingsRepository = settings,
    ): Pair<CounterStore, FakeEncounterRepository> {
        val clock = FakeClock(Instant.parse("2026-09-22T10:00:00Z"))
        val store = CounterStore(
            logTally = LogTally(encounterRepository, FakeIdGenerator(), FakeDeviceIdProvider(), clock, TimeZone.UTC),
            logPhoto = LogPhoto(
                encounterRepository = encounterRepository,
                placeCellRepository = FakePlaceCellRepository(),
                settingsRepository = settingsRepository,
                exifReader = exifReader,
                imageResizer = imageResizer,
                digest = FakeDigest(),
                gallerySaver = gallerySaver,
                idGenerator = FakeIdGenerator(),
                deviceIdProvider = FakeDeviceIdProvider(),
                clock = clock,
                timeZone = TimeZone.UTC,
            ),
            undoLastTally = UndoLastTally(encounterRepository, clock),
            undoImport = UndoImport(encounterRepository, clock),
            observeStats = ObserveStats(encounterRepository, clock, TimeZone.UTC, ticks = flowOf(Unit)),
            settingsRepository = settingsRepository,
            stateMapper = CounterStateMapper(FakeDateTimeFormatter()),
            locationPermissionRequestState = locationPermissionRequestState,
        )
        runCurrent()
        return store to encounterRepository
    }

    @Test
    fun `tapping the camera button asks the screen to open the camera`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()

        store.effects.test {
            store.dispatch(CounterIntent.CameraClicked)
            runCurrent()

            assertEquals(CounterEffect.OpenCamera, awaitItem())
        }
        assertEquals(emptyList(), repository.insertedIds)
    }

    @Test
    fun `a captured photo becomes an encounter`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()

        store.dispatch(CounterIntent.PhotoCaptured(SOURCE))
        runCurrent()

        assertEquals(1, repository.insertedIds.size)
    }

    @Test
    fun `a stored photo's original is discarded, so the cache does not grow by one photo per cat`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()

            store.effects.test {
                store.dispatch(CounterIntent.PhotoCaptured(SOURCE))
                runCurrent()

                val effects = buildList { repeat(2) { add(awaitItem()) } }
                assertEquals(
                    listOf(CounterEffect.DiscardCapture(SOURCE)),
                    effects.filterIsInstance<CounterEffect.DiscardCapture>()
                )
            }
        }

    @Test
    fun `an unreadable photo's original is discarded too`() = runTest(mainDispatcher) {
        imageResizer.result = null
        val (store, _) = newStore()

        store.effects.test {
            store.dispatch(CounterIntent.PhotoCaptured(SOURCE))
            runCurrent()

            assertEquals(CounterEffect.PhotoNotSaved, awaitItem())
            assertEquals(CounterEffect.DiscardCapture(SOURCE), awaitItem())
        }
    }

    @Test
    fun `a cancelled camera creates nothing`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()

        store.dispatch(CounterIntent.PhotoCaptured(null))
        runCurrent()

        assertEquals(emptyList(), repository.insertedIds)
    }

    @Test
    fun `an unreadable photo creates nothing and says so exactly once`() = runTest(mainDispatcher) {
        imageResizer.result = null
        val (store, repository) = newStore()

        store.effects.test {
            store.dispatch(CounterIntent.PhotoCaptured(SOURCE))
            runCurrent()

            assertEquals(CounterEffect.PhotoNotSaved, awaitItem())
            assertEquals(CounterEffect.DiscardCapture(SOURCE), awaitItem())
            expectNoEvents()
        }
        assertEquals(emptyList(), repository.insertedIds)
    }

    @Test
    fun `a photo without EXIF coordinates is handed to the background location attach`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()

            store.effects.test {
                store.dispatch(CounterIntent.PhotoCaptured(SOURCE))
                runCurrent()

                assertIs<CounterEffect.AttachLocation>(awaitItem())
                assertEquals(CounterEffect.DiscardCapture(SOURCE), awaitItem())
            }
        }

    @Test
    fun `a photo that already carries EXIF coordinates does not ask for a location fix`() =
        runTest(mainDispatcher) {
            exifReader.data = ExifData(lat = 41.39864, lon = 2.17842)
            val (store, _) = newStore()

            store.effects.test {
                store.dispatch(CounterIntent.PhotoCaptured(SOURCE))
                runCurrent()

                assertEquals(CounterEffect.DiscardCapture(SOURCE), awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `initial state has zero total and no undo chip`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        assertEquals(CounterState(totalLabel = "0", undoVisible = false), store.state.value)
    }

    @Test
    fun `three rapid taps log three cats with no debounce, one haptic tick each`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        repeat(3) { store.dispatch(CounterIntent.TallyClicked) }
        runCurrent()

        assertEquals("3", store.state.value.totalLabel)
        assertTrue(store.state.value.undoVisible)
        store.effects.test {
            assertEquals(CounterEffect.HapticTick, awaitItem())
            assertEquals(CounterEffect.RequestLocationPermission, awaitItem())
            assertEquals(CounterEffect.AttachLocation("id-1"), awaitItem())
            assertEquals(CounterEffect.HapticTick, awaitItem())
            assertEquals(CounterEffect.AttachLocation("id-2"), awaitItem())
            assertEquals(CounterEffect.HapticTick, awaitItem())
            assertEquals(CounterEffect.AttachLocation("id-3"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `only the first tally ever requests location permission`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()

        store.effects.test {
            assertEquals(CounterEffect.HapticTick, awaitItem())
            assertEquals(CounterEffect.RequestLocationPermission, awaitItem())
            assertEquals(CounterEffect.AttachLocation("id-1"), awaitItem())
            assertEquals(CounterEffect.HapticTick, awaitItem())
            assertEquals(CounterEffect.AttachLocation("id-2"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a fresh Store after process death does not re-request an already-requested permission`() =
        runTest(mainDispatcher) {
            // Simulates a killed-and-relaunched process: a brand new Store, but the persisted
            // flag survived.
            val (store, _) = newStore(locationPermissionRequestState = FakeLocationPermissionRequestState(true))

            store.effects.test {
                store.dispatch(CounterIntent.TallyClicked)
                runCurrent()
                assertEquals(CounterEffect.HapticTick, awaitItem())
                assertEquals(CounterEffect.AttachLocation("id-1"), awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `a denied permission result shows the hint, a granted one hides it`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        store.dispatch(CounterIntent.LocationPermissionResult(granted = false))
        runCurrent()
        assertTrue(store.state.value.locationPermissionHintVisible)

        store.dispatch(CounterIntent.LocationPermissionResult(granted = true))
        runCurrent()
        assertFalse(store.state.value.locationPermissionHintVisible)
    }

    @Test
    fun `dismissing the hint hides it without touching permission state`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        store.dispatch(CounterIntent.LocationPermissionResult(granted = false))
        runCurrent()

        store.dispatch(CounterIntent.LocationPermissionHintDismissed)
        runCurrent()

        assertFalse(store.state.value.locationPermissionHintVisible)
    }

    @Test
    fun `the grant action re-requests permission even after the first tally already asked once`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()

            store.effects.test {
                store.dispatch(CounterIntent.TallyClicked)
                runCurrent()
                assertEquals(CounterEffect.HapticTick, awaitItem())
                assertEquals(CounterEffect.RequestLocationPermission, awaitItem())
                assertEquals(CounterEffect.AttachLocation("id-1"), awaitItem())

                store.dispatch(CounterIntent.GrantLocationClicked)
                runCurrent()
                assertEquals(CounterEffect.RequestLocationPermission, awaitItem())
            }
        }

    @Test
    fun `the total tracks the repository flow rather than a store-local counter`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()

        // Written by something other than this Store's TallyClicked handling.
        repository.insert(externalEncounter(id = "external-1"))
        runCurrent()

        assertEquals("1", store.state.value.totalLabel)
    }

    @Test
    fun `undo targets the most recently created encounter and a second undo is a no-op`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        val newestId = repository.insertedIds.last()

        store.dispatch(CounterIntent.UndoClicked)
        runCurrent()
        store.dispatch(CounterIntent.UndoClicked)
        runCurrent()

        assertEquals(listOf(newestId), repository.softDeletedIds)
        assertEquals("1", store.state.value.totalLabel)
    }

    @Test
    fun `undo emits CancelLocationAttach for the target id, but a second undo does not`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        store.effects.test {
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()
            assertEquals(CounterEffect.HapticTick, awaitItem())
            assertEquals(CounterEffect.RequestLocationPermission, awaitItem())
            assertEquals(CounterEffect.AttachLocation("id-1"), awaitItem())

            store.dispatch(CounterIntent.UndoClicked)
            runCurrent()
            assertEquals(CounterEffect.CancelLocationAttach("id-1"), awaitItem())

            store.dispatch(CounterIntent.UndoClicked)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `a second tap restarts the undo window, which then expires and disables undo`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        val margin = 1.seconds

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        advanceTimeBy((Tuning.UNDO_VISIBLE - margin).inWholeMilliseconds)
        runCurrent()
        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()

        // Past the first tap's original deadline: still visible only because the second tap
        // restarted the window instead of the first tap's timer firing on schedule.
        advanceTimeBy((margin + margin).inWholeMilliseconds)
        runCurrent()
        assertTrue(store.state.value.undoVisible)

        advanceTimeBy(Tuning.UNDO_VISIBLE.inWholeMilliseconds)
        runCurrent()
        assertFalse(store.state.value.undoVisible)

        store.dispatch(CounterIntent.UndoClicked)
        runCurrent()
        assertEquals(emptyList<String>(), repository.softDeletedIds)
    }

    @Test
    fun `tapping shows a plus-one that grows with each tap in the same run`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        assertEquals(1, store.state.value.tapBurst)

        store.dispatch(CounterIntent.TallyClicked)
        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        assertEquals(3, store.state.value.tapBurst)
    }

    @Test
    fun `the burst fades once tapping stops, and the window restarts on every tap`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()

            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()
            advanceTimeBy(Tuning.TAP_BURST_VISIBLE - 1.milliseconds)
            runCurrent()
            assertEquals(1, store.state.value.tapBurst, "faded before the window closed")

            // A second tap here restarts the window rather than inheriting the first tap's.
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()
            advanceTimeBy(Tuning.TAP_BURST_VISIBLE - 1.milliseconds)
            runCurrent()
            assertEquals(2, store.state.value.tapBurst)

            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertNull(store.state.value.tapBurst)
        }

    @Test
    fun `a new run of taps starts counting from one again`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        advanceTimeBy(Tuning.TAP_BURST_VISIBLE + 1.milliseconds)
        runCurrent()

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()

        assertEquals(1, store.state.value.tapBurst)
    }

    @Test
    fun `the first cat is celebrated once and never again`() = runTest(mainDispatcher) {
        val celebrating = FakeSettingsRepository(lastMilestone = 0)
        val (store, _) = newStore(settingsRepository = celebrating)

        store.effects.test {
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()

            assertEquals(listOf(CounterEffect.MilestoneReached(1)), milestonesIn(cancelAndConsumeRemainingEvents()))
        }

        // A second Store over the same settings is the next launch: the milestone is spent.
        val (next, _) = newStore(settingsRepository = celebrating)
        next.effects.test {
            runCurrent()
            assertEquals(emptyList(), milestonesIn(cancelAndConsumeRemainingEvents()))
        }
    }

    @Test
    fun `a milestone already celebrated stays quiet`() = runTest(mainDispatcher) {
        val (store, _) = newStore(settingsRepository = FakeSettingsRepository(lastMilestone = 1))

        store.effects.test {
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()

            assertEquals(emptyList(), milestonesIn(cancelAndConsumeRemainingEvents()))
        }
    }

    @Test
    fun `tapping a coat logs a cat of that coat, without a second tap`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()

        store.dispatch(CounterIntent.CoatTallyClicked(CoatOption.GREY_WHITE))
        runCurrent()

        val logged = repository.encounters().single()
        assertEquals(CatCoat.GREY_WHITE, logged.coat)
        assertEquals("1", store.state.value.totalLabel)
    }

    @Test
    fun `the big button still logs a cat whose coat nobody noted`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()

        assertNull(repository.encounters().single().coat)
    }

    @Test
    fun `the grid shows which coat the undoable cat had, and forgets it once undone`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()

            store.dispatch(CounterIntent.CoatTallyClicked(CoatOption.BLACK))
            runCurrent()
            assertEquals(CoatOption.BLACK, store.state.value.lastCoat)

            store.dispatch(CounterIntent.UndoClicked)
            runCurrent()
            assertNull(store.state.value.lastCoat)
        }

    @Test
    fun `a coat tap is undoable like any other cat`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()

        store.dispatch(CounterIntent.CoatTallyClicked(CoatOption.GINGER))
        runCurrent()
        store.dispatch(CounterIntent.UndoClicked)
        runCurrent()

        assertEquals(1, repository.softDeletedIds.size)
    }

    @Test
    fun `a failed insert is swallowed instead of crashing the store, but the tap still ticks`() =
        runTest(mainDispatcher) {
            val repository = FakeEncounterRepository().apply { insertShouldThrow = IllegalStateException("disk full") }
            val (store, _) = newStore(encounterRepository = repository)

            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()

            // The burst and the tick are feedback for the tap itself, so they land either way;
            // the total comes from the database and stays put because nothing was written.
            assertEquals(
                CounterState(totalLabel = "0", undoVisible = false, tapBurst = 1),
                store.state.value,
            )
            store.effects.test {
                assertEquals(CounterEffect.HapticTick, awaitItem())
                assertEquals(CounterEffect.RequestLocationPermission, awaitItem())
            }
        }

    private companion object {
        const val SOURCE = "file:///cache/capture.jpg"
    }

    @Test
    fun `picking photos shows a progress row and asks the screen to start the import`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()
            store.effects.test {
                store.dispatch(CounterIntent.Import.PhotosPicked(persistentListOf("content://a", "content://b")))
                runCurrent()

                assertEquals(ImportProgressState(done = 0, total = 2), store.state.value.importProgress)
                assertEquals(CounterEffect.StartImport(persistentListOf("content://a", "content://b")), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismissing the picker starts nothing and leaves the screen as it was`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        store.dispatch(CounterIntent.Import.PhotosPicked(persistentListOf()))
        runCurrent()

        assertNull(store.state.value.importProgress)
        assertNull(store.state.value.importSummary)
    }

    @Test
    fun `a finished import replaces the progress row with a summary`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        store.dispatch(CounterIntent.Import.PhotosPicked(persistentListOf("content://a")))
        runCurrent()

        store.dispatch(CounterIntent.Import.Finished(persistentListOf("id-1", "id-2"), skipped = 3, failed = 1))
        runCurrent()

        assertNull(store.state.value.importProgress)
        assertEquals(
            ImportSummaryState(added = 2, skipped = 3, failed = 1, undoable = true),
            store.state.value.importSummary,
        )
    }

    @Test
    fun `a run where nothing went wrong says so by omission, not with zeroes`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        store.dispatch(CounterIntent.Import.Finished(persistentListOf("id-1"), skipped = 0, failed = 0))
        runCurrent()

        assertEquals(
            ImportSummaryState(added = 1, skipped = null, failed = null, undoable = true),
            store.state.value.importSummary,
        )
    }

    @Test
    fun `an import that added nothing offers no undo`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        store.dispatch(CounterIntent.Import.Finished(persistentListOf(), skipped = 2, failed = 0))
        runCurrent()

        assertEquals(false, store.state.value.importSummary?.undoable)
    }

    @Test
    fun `undoing an import soft-deletes every cat it added`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter(id = "id-1"))
        repository.insert(externalEncounter(id = "id-2"))
        store.dispatch(CounterIntent.Import.Finished(persistentListOf("id-1", "id-2"), skipped = 0, failed = 0))
        runCurrent()

        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        assertEquals(listOf("id-1", "id-2"), repository.softDeletedIds)
        assertEquals(false, store.state.value.importSummary?.undoable)
    }

    @Test
    fun `undoing an import twice deletes each cat only once`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter(id = "id-1"))
        store.dispatch(CounterIntent.Import.Finished(persistentListOf("id-1"), skipped = 0, failed = 0))
        runCurrent()

        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()
        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        assertEquals(listOf("id-1"), repository.softDeletedIds)
    }

    @Test
    fun `progress survives the stats flow re-emitting as each photo lands`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        store.dispatch(CounterIntent.Import.PhotosPicked(persistentListOf("content://a", "content://b")))
        runCurrent()
        store.dispatch(CounterIntent.Import.Progressed(done = 1, total = 2))
        runCurrent()

        // Every insert re-emits stats, which rebuilds the whole state through the mapper.
        repository.insert(externalEncounter(id = "imported"))
        runCurrent()

        assertEquals(ImportProgressState(done = 1, total = 2), store.state.value.importProgress)
    }
}
