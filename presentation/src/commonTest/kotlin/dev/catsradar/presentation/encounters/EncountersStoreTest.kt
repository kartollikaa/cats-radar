package dev.catsradar.presentation.encounters

import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.usecase.DeleteEncounters
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.UndoDeleteEncounters
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.counter.FakeSettingsRepository
import kotlinx.collections.immutable.persistentSetOf
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
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EncountersStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()
    private val settings = FakeSettingsRepository()
    private val clock = FakeClock(NOW)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty before any encounter is observed`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()

        assertTrue(store.state.value.isEmpty)
    }

    @Test
    fun `a logged encounter appears as a row once the repository emits it`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()

        repository.insert(encounterFixture("1", BASE))
        runCurrent()

        assertEquals(setOf("1"), store.rowIds())
    }

    @Test
    fun `turning the grid off re-lays the open tab as a list, with no new encounter`() = runTest(mainDispatcher) {
        settings.setEncountersGrid(true)
        val store = storeWith("1")
        val before = store.state.value.layout

        settings.setEncountersGrid(false)
        runCurrent()

        assertEquals(EncountersLayout.GRID, before)
        assertEquals(EncountersLayout.LIST, store.state.value.layout)
        assertEquals(1, store.state.value.rows.count { it is EncountersRow.Single })
    }

    @Test
    fun `a selection survives turning the grid off`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b")
        select(store, "a")

        settings.setEncountersGrid(false)
        runCurrent()

        assertEquals(persistentSetOf("a"), store.state.value.selectedIds)
        assertEquals(setOf("a"), store.selectedCatIds())
    }

    @Test
    fun `a tap outside selection opens the encounter and selects nothing`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b")

        store.effects.test {
            store.dispatch(EncountersIntent.EncounterClicked("a"))
            runCurrent()

            assertEquals(EncountersEffect.OpenEncounter("a"), awaitItem())
        }
        assertFalse(store.state.value.isSelecting)
    }

    @Test
    fun `a long press starts selecting with exactly that row selected and opens nothing`() =
        runTest(mainDispatcher) {
            val store = storeWith("a", "b")

            store.effects.test {
                store.dispatch(EncountersIntent.EncounterLongPressed("a"))
                runCurrent()

                expectNoEvents()
            }
            assertEquals(persistentSetOf("a"), store.state.value.selectedIds)
            assertEquals(setOf("a"), store.selectedCatIds())
        }

    @Test
    fun `a tap while selecting toggles the row and opens nothing`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b", "c")
        store.dispatch(EncountersIntent.EncounterLongPressed("a"))
        runCurrent()

        store.effects.test {
            store.dispatch(EncountersIntent.EncounterClicked("b"))
            runCurrent()
            assertEquals(persistentSetOf("a", "b"), store.state.value.selectedIds)

            store.dispatch(EncountersIntent.EncounterClicked("a"))
            runCurrent()
            assertEquals(persistentSetOf("b"), store.state.value.selectedIds)

            expectNoEvents()
        }
    }

    @Test
    fun `a long press while selecting toggles the row, as a tap does`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b")
        store.dispatch(EncountersIntent.EncounterLongPressed("a"))
        runCurrent()

        store.dispatch(EncountersIntent.EncounterLongPressed("b"))
        runCurrent()
        assertEquals(persistentSetOf("a", "b"), store.state.value.selectedIds)

        store.dispatch(EncountersIntent.EncounterLongPressed("a"))
        runCurrent()
        assertEquals(persistentSetOf("b"), store.state.value.selectedIds)
    }

    @Test
    fun `deselecting the last selected row leaves selection mode`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b")
        store.dispatch(EncountersIntent.EncounterLongPressed("a"))
        runCurrent()

        store.dispatch(EncountersIntent.EncounterClicked("a"))
        runCurrent()

        assertFalse(store.state.value.isSelecting)
        assertEquals(emptySet(), store.selectedCatIds())
    }

    @Test
    fun `dismissing the selection empties it and leaves selection mode`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b")
        store.dispatch(EncountersIntent.EncounterLongPressed("a"))
        store.dispatch(EncountersIntent.EncounterClicked("b"))
        runCurrent()

        store.dispatch(EncountersIntent.SelectionDismissed)
        runCurrent()

        assertFalse(store.state.value.isSelecting)
        assertEquals(emptySet(), store.selectedCatIds())
    }

    @Test
    fun `delete removes every selected cat, leaves selection mode and offers undo with the count`() =
        runTest(mainDispatcher) {
            val store = storeWith("a", "b", "c")
            select(store, "a", "c")

            store.dispatch(EncountersIntent.DeleteSelectedClicked)
            runCurrent()

            assertEquals(setOf("b"), store.rowIds())
            assertFalse(store.state.value.isSelecting)
            assertEquals(2, store.state.value.removedCount)
        }

    @Test
    fun `undo inside the window brings the whole batch back and hides the undo bar`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b", "c")
        select(store, "a", "c")
        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()

        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.seconds)
        store.dispatch(EncountersIntent.UndoClicked)
        runCurrent()

        assertEquals(setOf("a", "b", "c"), store.rowIds())
        assertEquals(null, store.state.value.removedCount)
    }

    @Test
    fun `the undo bar goes when the window closes, and an undo after that restores nothing`() =
        runTest(mainDispatcher) {
            val store = storeWith("a", "b")
            select(store, "a")
            store.dispatch(EncountersIntent.DeleteSelectedClicked)
            runCurrent()

            advanceTimeBy(Tuning.UNDO_VISIBLE - 1.milliseconds)
            runCurrent()
            assertEquals(1, store.state.value.removedCount)

            advanceTimeBy(1.milliseconds)
            runCurrent()
            assertEquals(null, store.state.value.removedCount)

            store.dispatch(EncountersIntent.UndoClicked)
            runCurrent()
            assertEquals(setOf("b"), store.rowIds())
        }

    @Test
    fun `a second delete inside the window replaces the undo with its own batch`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b", "c", "d")
        select(store, "a")
        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()

        select(store, "b", "c")
        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()
        assertEquals(2, store.state.value.removedCount)

        store.dispatch(EncountersIntent.UndoClicked)
        runCurrent()

        assertEquals(setOf("b", "c", "d"), store.rowIds())
    }

    @Test
    fun `the second batch keeps its undo for a whole window of its own`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b", "c")
        select(store, "a")
        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.seconds)

        select(store, "b")
        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.seconds)
        runCurrent()

        assertEquals(1, store.state.value.removedCount)
    }

    @Test
    fun `a selected cat deleted elsewhere drops out of the selection, and selection ends with the last`() =
        runTest(mainDispatcher) {
            val store = storeWith("a", "b", "c")
            select(store, "a", "b")

            repository.softDelete("a", NOW)
            runCurrent()
            assertEquals(persistentSetOf("b"), store.state.value.selectedIds)

            repository.softDelete("b", NOW)
            runCurrent()
            assertFalse(store.state.value.isSelecting)
        }

    @Test
    fun `a failed delete keeps the selection as it was and offers no undo`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b")
        select(store, "a", "b")
        repository.softDeleteAllShouldThrow = IllegalStateException("disk full")

        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()

        assertEquals(persistentSetOf("a", "b"), store.state.value.selectedIds)
        assertEquals(null, store.state.value.removedCount)
        assertEquals(setOf("a", "b"), store.rowIds())
    }

    @Test
    fun `a second delete while the first write is in flight writes nothing more`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b")
        select(store, "a")
        val gate = CompletableDeferred<Unit>()
        repository.softDeleteAllGate = gate

        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()
        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(listOf("a")), repository.softDeleteAllCalls)
    }

    @Test
    fun `a failed undo keeps the undo bar and a fresh window to try again`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b")
        select(store, "a")
        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.seconds)
        repository.undoDeleteAllShouldThrow = IllegalStateException("disk full")

        store.dispatch(EncountersIntent.UndoClicked)
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.seconds)
        runCurrent()
        assertEquals(1, store.state.value.removedCount)

        repository.undoDeleteAllShouldThrow = null
        store.dispatch(EncountersIntent.UndoClicked)
        runCurrent()
        assertEquals(setOf("a", "b"), store.rowIds())
    }

    @Test
    fun `a failed undo leaves a batch deleted meanwhile as the one to undo`() = runTest(mainDispatcher) {
        val store = storeWith("a", "b", "c")
        select(store, "a")
        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.undoDeleteAllGate = gate
        repository.undoDeleteAllShouldThrow = IllegalStateException("disk full")
        store.dispatch(EncountersIntent.UndoClicked)
        runCurrent()

        select(store, "b", "c")
        store.dispatch(EncountersIntent.DeleteSelectedClicked)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, store.state.value.removedCount)

        repository.undoDeleteAllGate = null
        repository.undoDeleteAllShouldThrow = null
        store.dispatch(EncountersIntent.UndoClicked)
        runCurrent()
        assertEquals(setOf("b", "c"), store.rowIds())
    }

    private fun newStore(): EncountersStore = EncountersStore(
        observeEncounters = ObserveEncounters(repository),
        settingsRepository = settings,
        deleteEncounters = DeleteEncounters(repository, clock),
        undoDeleteEncounters = UndoDeleteEncounters(repository),
        stateMapper = EncountersStateMapper(FakeDateTimeFormatter(), FakePhotoStorage()),
        clock = clock,
        timeZone = TimeZone.UTC,
    )

    private suspend fun TestScope.storeWith(vararg ids: String): EncountersStore {
        ids.forEachIndexed { index, id -> repository.insert(encounterFixture(id, BASE + (index * 5).minutes)) }
        val store = newStore()
        runCurrent()
        return store
    }

    private fun TestScope.select(store: EncountersStore, vararg ids: String) {
        ids.forEach { id ->
            val intent = if (store.state.value.isSelecting) {
                EncountersIntent.EncounterClicked(id)
            } else {
                EncountersIntent.EncounterLongPressed(id)
            }
            store.dispatch(intent)
            runCurrent()
        }
    }

    private fun EncountersStore.cats(): List<Pair<String, Boolean>> = state.value.rows.flatMap { row ->
        when (row) {
            is OutingHeader -> emptyList()
            is EncountersRow.PhotoPair -> listOf(row.first, row.second).map { it.id to it.selected }
            is EncountersRow.Tiles -> row.cells.map { it.id to it.selected }
            is EncountersRow.Cards -> row.cells.map { it.id to it.selected }
            is EncountersRow.Single -> listOf(row.cell.id to row.cell.selected)
        }
    }

    private fun EncountersStore.rowIds(): Set<String> = cats().map { it.first }.toSet()

    private fun EncountersStore.selectedCatIds(): Set<String> = cats().filter { it.second }.map { it.first }.toSet()

    private companion object {
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
    }
}
