package dev.catsradar.presentation.counter

import app.cash.turbine.Event
import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.presentation.coat.CoatOption
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CounterStoreUndoTest {

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
    fun `undo walks a run of taps back newest first until every cat of it is gone`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repeat(3) {
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()
        }

        repeat(4) {
            store.dispatch(CounterIntent.UndoClicked)
            runCurrent()
        }

        assertEquals(listOf("id-3", "id-2", "id-1"), repository.softDeletedIds)
        assertEquals("0", store.state.value.totalLabel)
    }

    @Test
    fun `each undo cancels the location attach of the cat it takes away, and only that one`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()

            store.effects.test {
                repeat(2) {
                    store.dispatch(CounterIntent.TallyClicked)
                    runCurrent()
                }
                repeat(3) {
                    store.dispatch(CounterIntent.UndoClicked)
                    runCurrent()
                }

                assertEquals(
                    listOf(CounterEffect.CancelLocationAttach("id-2"), CounterEffect.CancelLocationAttach("id-1")),
                    cancelAndConsumeRemainingEvents()
                        .filterIsInstance<Event.Item<CounterEffect>>()
                        .map { it.value }
                        .filterIsInstance<CounterEffect.CancelLocationAttach>(),
                )
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
        assertEquals(2, store.state.value.tapBurst)

        advanceTimeBy(Tuning.UNDO_VISIBLE.inWholeMilliseconds)
        runCurrent()
        assertFalse(store.state.value.undoVisible)
        assertNull(store.state.value.tapBurst)

        store.dispatch(CounterIntent.UndoClicked)
        runCurrent()
        assertEquals(emptyList<String>(), repository.softDeletedIds)
    }

    @Test
    fun `a lone tap's undo expires on its own and then takes nothing away`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
        advanceTimeBy((Tuning.UNDO_VISIBLE - 1.milliseconds).inWholeMilliseconds)
        runCurrent()
        assertTrue(store.state.value.undoVisible)

        advanceTimeBy(1.milliseconds.inWholeMilliseconds)
        runCurrent()
        assertFalse(store.state.value.undoVisible)

        store.dispatch(CounterIntent.UndoClicked)
        runCurrent()
        assertEquals(emptyList<String>(), repository.softDeletedIds)
    }

    @Test
    fun `the chip stays while the run still has a cat to undo and goes with the last one`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()
            repeat(2) {
                store.dispatch(CounterIntent.TallyClicked)
                runCurrent()
            }

            store.dispatch(CounterIntent.UndoClicked)
            runCurrent()
            assertTrue(store.state.value.undoVisible)

            store.dispatch(CounterIntent.UndoClicked)
            runCurrent()
            assertFalse(store.state.value.undoVisible)
        }

    @Test
    fun `each undo takes one off the burst, and the last one takes the burst away`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        repeat(3) {
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()
        }

        val bursts = List(3) {
            store.dispatch(CounterIntent.UndoClicked)
            runCurrent()
            store.state.value.tapBurst
        }

        assertEquals(listOf(2, 1, null), bursts)
    }

    @Test
    fun `a tap after an undo counts on from what the burst has left`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        repeat(3) {
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()
        }
        store.dispatch(CounterIntent.UndoClicked)
        runCurrent()

        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()

        assertEquals(3, store.state.value.tapBurst)
    }

    @Test
    fun `a tap whose write fails takes its one back off the burst`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()

        repository.insertShouldThrow = IllegalStateException("disk full")
        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()

        assertTrue(store.state.value.undoVisible)
        assertEquals(1, store.state.value.tapBurst)
    }

    @Test
    fun `a tap still being written when the window closes keeps its place on the burst`() =
        runTest(mainDispatcher) {
            val (store, repository) = newStore()
            val write = 200.milliseconds
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()
            advanceTimeBy((Tuning.UNDO_VISIBLE - write / 2).inWholeMilliseconds)
            runCurrent()

            repository.insertDelays += write
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()
            assertEquals(2, store.state.value.tapBurst)

            advanceTimeBy((write / 2).inWholeMilliseconds)
            runCurrent()
            assertFalse(store.state.value.undoVisible)
            assertEquals(1, store.state.value.tapBurst)

            advanceTimeBy((write / 2).inWholeMilliseconds)
            runCurrent()
            assertTrue(store.state.value.undoVisible)
            assertEquals(1, store.state.value.tapBurst)
        }

    @Test
    fun `each undo restarts the window for the cats still left in the run`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        val justShort = Tuning.UNDO_VISIBLE - 1.milliseconds
        repeat(3) {
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()
        }

        repeat(2) {
            advanceTimeBy(justShort.inWholeMilliseconds)
            runCurrent()
            assertTrue(store.state.value.undoVisible)
            store.dispatch(CounterIntent.UndoClicked)
            runCurrent()
        }
        advanceTimeBy(justShort.inWholeMilliseconds)
        runCurrent()
        assertTrue(store.state.value.undoVisible)

        advanceTimeBy(1.milliseconds.inWholeMilliseconds)
        runCurrent()
        assertFalse(store.state.value.undoVisible)
        store.dispatch(CounterIntent.UndoClicked)
        runCurrent()
        assertEquals(listOf("id-3", "id-2"), repository.softDeletedIds)
    }

    @Test
    fun `two undos dispatched back to back take away two different cats`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repeat(2) {
            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()
        }
        repository.softDeleteDelay = 100.milliseconds

        store.dispatch(CounterIntent.UndoClicked)
        store.dispatch(CounterIntent.UndoClicked)
        advanceTimeBy(1.seconds.inWholeMilliseconds)
        runCurrent()

        assertEquals(listOf("id-2", "id-1"), repository.softDeletedIds)
    }

    @Test
    fun `undo follows the order of the taps, not the order their writes finished in`() =
        runTest(mainDispatcher) {
            val (store, repository) = newStore()
            repository.insertDelays += listOf(100.milliseconds, Duration.ZERO)

            store.dispatch(CounterIntent.TallyClicked)
            store.dispatch(CounterIntent.TallyClicked)
            advanceTimeBy(1.seconds.inWholeMilliseconds)
            runCurrent()
            assertEquals(listOf("id-2", "id-1"), repository.insertedIds)

            repeat(2) {
                store.dispatch(CounterIntent.UndoClicked)
                runCurrent()
            }
            assertEquals(listOf("id-2", "id-1"), repository.softDeletedIds)
        }

    @Test
    fun `an older tap's write landing late does not stretch the window of the newer one`() =
        runTest(mainDispatcher) {
            val (store, repository) = newStore()
            repository.insertDelays += listOf(Tuning.UNDO_VISIBLE - 1.seconds, Duration.ZERO)

            store.dispatch(CounterIntent.TallyClicked)
            store.dispatch(CounterIntent.TallyClicked)
            advanceTimeBy(Tuning.UNDO_VISIBLE.inWholeMilliseconds)
            runCurrent()

            assertEquals(listOf("id-2", "id-1"), repository.insertedIds)
            assertFalse(store.state.value.undoVisible)
        }

    @Test
    fun `a tap whose write lands after the window closed does not reopen it`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        repository.insertDelays += listOf(Tuning.UNDO_VISIBLE * 2, Duration.ZERO)

        store.dispatch(CounterIntent.TallyClicked)
        store.dispatch(CounterIntent.TallyClicked)
        advanceTimeBy(Tuning.UNDO_VISIBLE.inWholeMilliseconds)
        runCurrent()
        assertNull(store.state.value.tapBurst)

        advanceTimeBy(Tuning.UNDO_VISIBLE.inWholeMilliseconds)
        runCurrent()
        assertEquals(listOf("id-2", "id-1"), repository.insertedIds)
        assertFalse(store.state.value.undoVisible)

        store.dispatch(CounterIntent.UndoClicked)
        runCurrent()
        assertEquals(emptyList<String>(), repository.softDeletedIds)
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
    fun `after an undo the grid shows the coat of the newest cat still in the run`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        store.dispatch(CounterIntent.CoatTallyClicked(CoatOption.BLACK))
        runCurrent()
        store.dispatch(CounterIntent.CoatTallyClicked(CoatOption.GINGER))
        runCurrent()

        store.dispatch(CounterIntent.UndoClicked)
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
}
