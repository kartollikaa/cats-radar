package dev.catsradar.presentation.counter

import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveEncounterCount
import dev.catsradar.domain.usecase.UndoLastTally
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

    private fun TestScope.newStore(
        encounterRepository: FakeEncounterRepository = FakeEncounterRepository(),
    ): Pair<CounterStore, FakeEncounterRepository> {
        val clock = FakeClock(Instant.parse("2026-09-22T10:00:00Z"))
        val store = CounterStore(
            logTally = LogTally(encounterRepository, FakeIdGenerator(), FakeDeviceIdProvider(), clock, TimeZone.UTC),
            undoLastTally = UndoLastTally(encounterRepository, clock),
            observeEncounterCount = ObserveEncounterCount(encounterRepository),
            stateMapper = CounterStateMapper(),
        )
        runCurrent()
        return store to encounterRepository
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
            assertEquals(CounterEffect.HapticTick, awaitItem())
            assertEquals(CounterEffect.HapticTick, awaitItem())
            expectNoEvents()
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
    fun `a failed insert is swallowed instead of crashing the store, but the tap still ticks`() =
        runTest(mainDispatcher) {
            val repository = FakeEncounterRepository().apply { insertShouldThrow = IllegalStateException("disk full") }
            val (store, _) = newStore(encounterRepository = repository)

            store.dispatch(CounterIntent.TallyClicked)
            runCurrent()

            assertEquals(CounterState(totalLabel = "0", undoVisible = false), store.state.value)
            store.effects.test { assertEquals(CounterEffect.HapticTick, awaitItem()) }
        }
}
