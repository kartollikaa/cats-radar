package dev.catsradar.presentation.counter

import dev.catsradar.presentation.encounters.withPhoto
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CounterStoreImportSummaryTest {

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
    fun `a finished import shows the first three added cats' photos`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter("plain"))
        listOf("b", "c", "d", "e").forEach {
            repository.insert(externalEncounter(it).withPhoto(photoPath = "$it.jpg", thumbPath = "${it}_thumb.jpg"))
        }
        runCurrent()

        store.dispatch(
            CounterIntent.Import.Finished(
                "run-1",
                persistentListOf("plain", "b", "c", "d", "e"),
                skipped = 0,
                failed = 0
            ),
        )
        runCurrent()

        assertEquals(
            persistentListOf("/data/photos/b_thumb.jpg", "/data/photos/c_thumb.jpg", "/data/photos/d_thumb.jpg"),
            store.state.value.importSummary?.thumbPaths,
        )
    }

    @Test
    fun `a run finishing while an older run's photos are read keeps its own summary and its own Undo`() =
        runTest(mainDispatcher) {
            val (store, repository) = newStore()
            repository.insert(externalEncounter("old").withPhoto(photoPath = "old.jpg", thumbPath = "old_thumb.jpg"))
            repository.insert(externalEncounter("new").withPhoto(photoPath = "new.jpg", thumbPath = "new_thumb.jpg"))
            val oldLookup = CompletableDeferred<Unit>()
            repository.lookupGates["old"] = oldLookup
            runCurrent()

            store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf("old"), skipped = 0, failed = 0))
            runCurrent()
            store.dispatch(CounterIntent.Import.Finished("run-2", persistentListOf("new"), skipped = 1, failed = 0))
            runCurrent()
            oldLookup.complete(Unit)
            runCurrent()
            val summary = store.state.value.importSummary
            store.dispatch(CounterIntent.Import.UndoClicked)
            runCurrent()

            val newRun = ImportSummaryState(
                added = 1,
                skipped = 1,
                failed = null,
                undoable = true,
                thumbPaths = persistentListOf("/data/photos/new_thumb.jpg"),
            )
            assertEquals(newRun, summary)
            assertEquals(listOf(listOf("new")), repository.softDeleteAllCalls)
        }

    @Test
    fun `a finished import whose photos cannot be read still says what it added`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter("a").withPhoto(photoPath = "a.jpg", thumbPath = "a_thumb.jpg"))
        val unreadable = CompletableDeferred<Unit>().apply { completeExceptionally(IllegalStateException()) }
        repository.lookupGates["a"] = unreadable
        runCurrent()

        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf("a"), skipped = 0, failed = 0))
        runCurrent()

        assertEquals(
            ImportSummaryState(added = 1, skipped = null, failed = null, undoable = true),
            store.state.value.importSummary,
        )
    }

    @Test
    fun `closing a summary whose Undo was on offer lets the Undo lapse`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insert(externalEncounter(id = "id-1"))
        store.dispatch(CounterIntent.Import.Finished("run-1", persistentListOf("id-1"), skipped = 0, failed = 0))
        runCurrent()

        store.dispatch(CounterIntent.Import.SummaryDismissed)
        runCurrent()
        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()

        assertEquals(emptyList(), repository.softDeleteAllCalls)
    }
}
