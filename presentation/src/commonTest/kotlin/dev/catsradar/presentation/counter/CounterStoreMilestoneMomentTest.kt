package dev.catsradar.presentation.counter

import dev.catsradar.domain.Tuning
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CounterStoreMilestoneMomentTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val firstCat = MilestoneMomentState(value = 1)

    private fun TestScope.newStore(
        settings: FakeSettingsRepository = FakeSettingsRepository(lastMilestone = 0),
    ): Pair<CounterStore, FakeEncounterRepository> {
        val repository = FakeEncounterRepository()
        return newCounterStore(encounterRepository = repository, settingsRepository = settings) to repository
    }

    private fun TestScope.tap(store: CounterStore) {
        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
    }

    @Test
    fun `a tap that lands on a rung celebrates it`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        tap(store)

        assertEquals(firstCat, store.state.value.milestoneMoment)
    }

    @Test
    fun `the rung is recorded as seen before the moment shows`() = runTest(mainDispatcher) {
        val settings = FakeSettingsRepository(lastMilestone = 0)
        val recorded = CompletableDeferred<Unit>()
        settings.lastSeenGate = recorded
        val (store, _) = newStore(settings)

        tap(store)
        val beforeRecorded = store.state.value.milestoneMoment
        recorded.complete(Unit)
        runCurrent()

        assertNull(beforeRecorded)
        assertEquals(firstCat to 1, store.state.value.milestoneMoment to settings.lastSeenMilestone().first())
    }

    @Test
    fun `the moment lasts until the undo window closes, then the ring heads for the next rung`() =
        runTest(mainDispatcher) {
            val (store, _) = newStore()
            tap(store)

            advanceTimeBy(Tuning.UNDO_VISIBLE - 1.milliseconds)
            runCurrent()
            assertEquals(firstCat, store.state.value.milestoneMoment)
            advanceTimeBy(1.milliseconds)
            runCurrent()

            assertNull(store.state.value.milestoneMoment)
            assertEquals("10", store.state.value.milestone?.next?.valueLabel)
        }

    @Test
    fun `further taps keep the moment until the window they restart closes`() = runTest(mainDispatcher) {
        val (store, _) = newStore()
        tap(store)
        advanceTimeBy(3.seconds)
        tap(store)

        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.milliseconds)
        runCurrent()
        assertEquals(firstCat, store.state.value.milestoneMoment)
        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertNull(store.state.value.milestoneMoment)
    }

    @Test
    fun `an undo below the rung takes the moment back, and landing on it again celebrates again`() =
        runTest(mainDispatcher) {
            val settings = FakeSettingsRepository(lastMilestone = 0)
            val (store, _) = newStore(settings)
            tap(store)

            store.dispatch(CounterIntent.UndoClicked)
            runCurrent()
            val afterUndo = store.state.value.milestoneMoment to settings.lastSeenMilestone().first()
            tap(store)

            assertEquals(null to 0, afterUndo)
            assertEquals(firstCat, store.state.value.milestoneMoment)
        }

    @Test
    fun `a rung reached with no run open shows for the undo window's length`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        runCurrent()

        repository.insert(externalEncounter("widget-cat"))
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.milliseconds)
        runCurrent()
        assertEquals(firstCat, store.state.value.milestoneMoment)
        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertNull(store.state.value.milestoneMoment)
    }

    @Test
    fun `a run that opens after the moment began keeps it until that run closes`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        runCurrent()
        repository.insert(externalEncounter("widget-cat"))
        runCurrent()

        advanceTimeBy(3.seconds)
        tap(store)
        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.milliseconds)
        runCurrent()
        assertEquals(firstCat, store.state.value.milestoneMoment)
        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertNull(store.state.value.milestoneMoment)
    }

    @Test
    fun `undoing a jump past a rung restores what was seen before it, so the skipped rung still celebrates`() =
        runTest(mainDispatcher) {
            val settings = FakeSettingsRepository(lastMilestone = 50)
            val (store, repository) = newStore(settings)
            repeat(99) { repository.insert(externalEncounter("old-$it")) }
            runCurrent()
            val imported = List(161) { "imported-$it" }
            imported.forEach { repository.insert(externalEncounter(it)) }
            runCurrent()
            val jumped = store.state.value.milestoneMoment
            store.dispatch(CounterIntent.Import.Finished("run-1", imported.toPersistentList(), skipped = 0, failed = 0))
            runCurrent()

            store.dispatch(CounterIntent.Import.UndoClicked)
            runCurrent()
            val afterUndo = settings.lastSeenMilestone().first()
            tap(store)

            assertEquals(MilestoneMomentState(250) to 50, jumped to afterUndo)
            assertEquals(MilestoneMomentState(100), store.state.value.milestoneMoment)
        }

    @Test
    fun `an import undone after its moment has ended still forgets the rung it jumped to`() = runTest(mainDispatcher) {
        val settings = FakeSettingsRepository(lastMilestone = 50)
        val (store, repository) = newStore(settings)
        repeat(99) { repository.insert(externalEncounter("old-$it")) }
        runCurrent()
        val imported = List(161) { "imported-$it" }
        imported.forEach { repository.insert(externalEncounter(it)) }
        runCurrent()
        store.dispatch(CounterIntent.Import.Finished("run-1", imported.toPersistentList(), skipped = 0, failed = 0))
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE + 1.seconds)
        runCurrent()
        val ended = store.state.value.milestoneMoment

        store.dispatch(CounterIntent.Import.UndoClicked)
        runCurrent()
        val afterUndo = settings.lastSeenMilestone().first()

        assertEquals(null to 50, ended to afterUndo)
    }

    @Test
    fun `a rung already seen shows no moment`() = runTest(mainDispatcher) {
        val (store, _) = newStore(FakeSettingsRepository(lastMilestone = 1))

        tap(store)

        assertNull(store.state.value.milestoneMoment)
    }

    @Test
    fun `the next launch does not celebrate the same rung again`() = runTest(mainDispatcher) {
        val settings = FakeSettingsRepository(lastMilestone = 0)
        val (store, repository) = newStore(settings)
        tap(store)
        advanceTimeBy(Tuning.UNDO_VISIBLE)
        runCurrent()

        val next = newCounterStore(encounterRepository = repository, settingsRepository = settings)
        runCurrent()

        assertEquals(1 to null, store.state.value.count to next.state.value.milestoneMoment)
    }
}
