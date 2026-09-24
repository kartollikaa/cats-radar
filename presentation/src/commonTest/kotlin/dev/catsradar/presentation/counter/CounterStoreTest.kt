package dev.catsradar.presentation.counter

import app.cash.turbine.Event
import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.platform.ExifData
import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    private val exifReader = FakeExifReader()
    private val imageResizer = FakeImageResizer()

    private fun milestonesIn(events: List<Event<CounterEffect>>): List<CounterEffect.MilestoneReached> =
        events.filterIsInstance<Event.Item<CounterEffect>>()
            .map { it.value }
            .filterIsInstance<CounterEffect.MilestoneReached>()

    private fun TestScope.newStore(
        encounterRepository: FakeEncounterRepository = FakeEncounterRepository(),
        locationPermissionRequestState: FakeLocationPermissionRequestState = FakeLocationPermissionRequestState(),
        settingsRepository: FakeSettingsRepository = milestonesAlreadyCelebrated(),
        ticks: Flow<Unit> = flowOf(Unit),
    ): Pair<CounterStore, FakeEncounterRepository> = newCounterStore(
        encounterRepository = encounterRepository,
        locationPermissionRequestState = locationPermissionRequestState,
        settingsRepository = settingsRepository,
        exifReader = exifReader,
        imageResizer = imageResizer,
        ticks = ticks,
    ) to encounterRepository

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
    fun `an empty history reads as zero with no undo chip`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        runCurrent()

        assertEquals(CounterState(totalLabel = "0", count = 0, undoVisible = false), store.state.value)
    }

    @Test
    fun `the total is unknown until the history has been read`() = runTest(mainDispatcher) {
        val (store, _) = newStore(ticks = emptyFlow())
        runCurrent()

        assertEquals(CounterState(totalLabel = "", count = null, undoVisible = false), store.state.value)
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
    fun `the plus-one lands before the write does`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insertDelays += 1.seconds

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()

        assertEquals(emptyList(), repository.insertedIds)
        assertEquals(1, store.state.value.tapBurst)
    }

    @Test
    fun `the burst stays up as long as undo does, and goes with it`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.milliseconds)
        runCurrent()
        assertTrue(store.state.value.undoVisible)
        assertEquals(1, store.state.value.tapBurst, "faded while undo was still up")

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertFalse(store.state.value.undoVisible)
        assertNull(store.state.value.tapBurst)
    }

    @Test
    fun `a new run of taps starts counting from one again`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE + 1.milliseconds)
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
    fun `a failed insert is swallowed instead of crashing the store, but the tap still ticks`() =
        runTest(mainDispatcher) {
            val repository = FakeEncounterRepository().apply { insertShouldThrow = IllegalStateException("disk full") }
            val (store, _) = newStore(encounterRepository = repository)

            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()

            assertEquals(CounterState(totalLabel = "0", count = 0, undoVisible = false), store.state.value)
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

        store.dispatch(
            CounterIntent.Import.Finished("run-1", persistentListOf("id-1", "id-2"), skipped = 3, failed = 1),
        )
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
        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0))
        runCurrent()

        assertEquals(
            ImportSummaryState(added = 1, skipped = null, failed = null, undoable = true),
            store.state.value.importSummary,
        )
    }

    @Test
    fun `an import that added nothing offers no undo`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf(), skipped = 2, failed = 0))
        runCurrent()

        assertEquals(false, store.state.value.importSummary?.undoable)
    }

    @Test
    fun `undoing an import soft-deletes every cat it added`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter(id = "id-1"))
        repository.insert(externalEncounter(id = "id-2"))
        store.dispatch(
            CounterIntent.Import.Finished("run-1", persistentListOf("id-1", "id-2"), skipped = 0, failed = 0),
        )
        runCurrent()

        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        assertEquals(listOf(listOf("id-1", "id-2")), repository.softDeleteAllCalls)
        assertEquals(emptyList(), repository.encounters().filter { it.deletedAt == null })
        assertEquals(false, store.state.value.importSummary?.undoable)
    }

    @Test
    fun `a failed undo of an import keeps the cats and the undo, and a retry takes them back`() =
        runTest(mainDispatcher) {
            val (store, repository) = newStore()
            repository.insert(externalEncounter(id = "id-1"))
            store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0))
            runCurrent()
            repository.softDeleteAllShouldThrow = IllegalStateException("disk full")

            store.dispatch(CounterIntent.Import.UndoClicked)
            runCurrent()
            assertEquals(true, store.state.value.importSummary?.undoable)
            assertEquals(listOf("id-1"), repository.encounters().filter { it.deletedAt == null }.map { it.id })

            repository.softDeleteAllShouldThrow = null
            store.dispatch(CounterIntent.Import.UndoClicked)
            runCurrent()
            assertEquals(false, store.state.value.importSummary?.undoable)
            assertEquals(emptyList(), repository.encounters().filter { it.deletedAt == null })
        }

    @Test
    fun `undoing an import twice deletes each cat only once`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter(id = "id-1"))
        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0))
        runCurrent()

        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()
        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        assertEquals(listOf(listOf("id-1")), repository.softDeleteAllCalls)
    }

    @Test
    fun `an undone import is not offered again when its result is read back`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter(id = "id-1"))
        val finished = CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0)
        store.dispatch(finished)
        runCurrent()
        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        store.dispatch(finished)
        runCurrent()
        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        assertEquals(false, store.state.value.importSummary?.undoable)
        assertEquals(listOf(listOf("id-1")), repository.softDeleteAllCalls)
    }

    @Test
    fun `an undone import is not reported by a new screen reading it back`() = runTest(mainDispatcher) {
        val settings = milestonesAlreadyCelebrated()
        val encounters = FakeEncounterRepository()
        encounters.insert(externalEncounter(id = "id-1"))
        val finished = CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0)
        val (first, _) = newStore(encounterRepository = encounters, settingsRepository = settings)
        first.dispatch(finished)
        runCurrent()
        first.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        val (second, _) = newStore(encounterRepository = encounters, settingsRepository = settings)
        second.dispatch(finished)
        runCurrent()

        assertNull(second.state.value.importSummary)
    }

    @Test
    fun `a dismissed import summary is not shown by a new screen reading it back`() = runTest(mainDispatcher) {
        val settings = milestonesAlreadyCelebrated()
        val finished = CounterIntent.Import.Finished("run-1", persistentListOf(), skipped = 2, failed = 0)
        val (first, _) = newStore(settingsRepository = settings)
        first.dispatch(finished)
        runCurrent()
        first.dispatch(CounterIntent.Import.SummaryDismissed)
        runCurrent()

        val (second, _) = newStore(settingsRepository = settings)
        second.dispatch(finished)
        runCurrent()

        assertNull(second.state.value.importSummary)
    }

    @Test
    fun `an import summary nobody has dealt with is shown again by a new screen`() = runTest(mainDispatcher) {
        val settings = milestonesAlreadyCelebrated()
        val finished = CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0)
        val (first, _) = newStore(settingsRepository = settings)
        first.dispatch(finished)
        runCurrent()

        val (second, _) = newStore(settingsRepository = settings)
        second.dispatch(finished)
        runCurrent()

        assertEquals(
            ImportSummaryState(added = 1, skipped = null, failed = null, undoable = true),
            second.state.value.importSummary,
        )
    }

    @Test
    fun `a newer import is reported after an earlier one was dismissed`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf(), skipped = 1, failed = 0))
        runCurrent()
        store.dispatch(CounterIntent.Import.SummaryDismissed)
        runCurrent()

        store.dispatch(CounterIntent.Import.Finished("run-2", persistentListOf("id-2"), skipped = 0, failed = 0))
        runCurrent()

        assertEquals(
            ImportSummaryState(added = 1, skipped = null, failed = null, undoable = true),
            store.state.value.importSummary,
        )
    }

    @Test
    fun `a dealt-with import read back again leaves a new import's progress row alone`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()
            val finished = CounterIntent.Import.Finished("run-1", persistentListOf(), skipped = 1, failed = 0)
            store.dispatch(finished)
            runCurrent()
            store.dispatch(CounterIntent.Import.SummaryDismissed)
            store.dispatch(CounterIntent.Import.PhotosPicked(persistentListOf("content://a")))
            runCurrent()

            store.dispatch(finished)
            runCurrent()

            assertEquals(ImportProgressState(done = 0, total = 1), store.state.value.importProgress)
            assertNull(store.state.value.importSummary)
        }

    @Test
    fun `a failed undo leaves the import on offer for a new screen`() = runTest(mainDispatcher) {
        val settings = milestonesAlreadyCelebrated()
        val encounters = FakeEncounterRepository()
        encounters.insert(externalEncounter(id = "id-1"))
        encounters.softDeleteAllShouldThrow = IllegalStateException("disk full")
        val finished = CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0)
        val (first, _) = newStore(encounterRepository = encounters, settingsRepository = settings)
        first.dispatch(finished)
        runCurrent()
        first.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        val (second, _) = newStore(encounterRepository = encounters, settingsRepository = settings)
        second.dispatch(finished)
        runCurrent()

        assertEquals(true, second.state.value.importSummary?.undoable)
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
