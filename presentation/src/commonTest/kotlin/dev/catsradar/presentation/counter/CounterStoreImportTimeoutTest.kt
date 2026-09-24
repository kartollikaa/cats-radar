package dev.catsradar.presentation.counter

import dev.catsradar.domain.Tuning
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CounterStoreImportTimeoutTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newStore(): Pair<CounterStore, FakeEncounterRepository> {
        val repository = FakeEncounterRepository()
        return newCounterStore(encounterRepository = repository) to repository
    }

    @Test
    fun `an import summary goes away by itself once its time is up`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0))
        runCurrent()

        advanceTimeBy(Tuning.IMPORT_SUMMARY_VISIBLE - 1.milliseconds)
        runCurrent()
        assertEquals(
            CounterState(
                totalLabel = "0",
                count = 0,
                undoVisible = false,
                importSummary = ImportSummaryState(added = 1, skipped = null, failed = null, undoable = true),
            ),
            store.state.value,
        )

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(CounterState(totalLabel = "0", count = 0, undoVisible = false), store.state.value)
    }

    @Test
    fun `an import summary with nothing to undo goes away by itself as well`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf(), skipped = 2, failed = 0))
        runCurrent()

        advanceTimeBy(Tuning.IMPORT_SUMMARY_VISIBLE)
        runCurrent()

        assertEquals(CounterState(totalLabel = "0", count = 0, undoVisible = false), store.state.value)
    }

    @Test
    fun `an undone import's summary goes away by itself as well`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter(id = "id-1"))
        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0))
        runCurrent()
        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        advanceTimeBy(Tuning.IMPORT_SUMMARY_VISIBLE)
        runCurrent()

        assertEquals(CounterState(totalLabel = "0", count = 0, undoVisible = false), store.state.value)
    }

    @Test
    fun `a timed-out import summary is not shown by a new screen reading it back`() = runTest(mainDispatcher) {
        val settings = milestonesAlreadyCelebrated()
        val finished = CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0)
        val first = newCounterStore(settingsRepository = settings)
        first.dispatch(finished)
        runCurrent()
        advanceTimeBy(Tuning.IMPORT_SUMMARY_VISIBLE)
        runCurrent()

        val second = newCounterStore(settingsRepository = settings)
        second.dispatch(finished)
        runCurrent()

        assertEquals(CounterState(totalLabel = "0", count = 0, undoVisible = false), second.state.value)
    }

    @Test
    fun `an undo arriving after the import summary timed out takes nothing back`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter(id = "id-1"))
        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0))
        runCurrent()
        advanceTimeBy(Tuning.IMPORT_SUMMARY_VISIBLE)
        runCurrent()

        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        assertEquals(emptyList(), repository.softDeleteAllCalls)
        assertEquals(CounterState(totalLabel = "1", count = 1, undoVisible = false), store.state.value)
    }

    @Test
    fun `an undo that fails after the import summary timed out leaves nothing to undo`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter(id = "id-1"))
        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0))
        runCurrent()
        val undoWrite = CompletableDeferred<Unit>()
        repository.softDeleteAllGate = undoWrite
        repository.softDeleteAllShouldThrow = IllegalStateException("disk full")
        advanceTimeBy(Tuning.IMPORT_SUMMARY_VISIBLE - 1.milliseconds)
        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()
        advanceTimeBy(1.milliseconds)
        runCurrent()
        undoWrite.complete(Unit)
        runCurrent()
        repository.softDeleteAllGate = null
        repository.softDeleteAllShouldThrow = null

        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        assertEquals(listOf(listOf("id-1")), repository.softDeleteAllCalls)
        assertEquals(CounterState(totalLabel = "1", count = 1, undoVisible = false), store.state.value)
    }

    @Test
    fun `a newer import summary is not cut short by the earlier one's time running out`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()
            store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf(), skipped = 1, failed = 0))
            runCurrent()
            advanceTimeBy(Tuning.IMPORT_SUMMARY_VISIBLE - 1.seconds)
            store.dispatch(CounterIntent.Import.PhotosPicked(persistentListOf("content://a")))
            store.dispatch(CounterIntent.Import.Finished("run-2", persistentListOf("id-2"), skipped = 0, failed = 0))
            runCurrent()

            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals(
                CounterState(
                    totalLabel = "0",
                    count = 0,
                    undoVisible = false,
                    importSummary = ImportSummaryState(added = 1, skipped = null, failed = null, undoable = true),
                ),
                store.state.value,
            )
        }
}
