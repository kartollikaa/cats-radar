package dev.catsradar.presentation.counter

import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class CounterStoreWalkTest {

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
        settingsRepository: FakeSettingsRepository = milestonesAlreadyCelebrated(),
        walkRepository: FakeWalkRepository = FakeWalkRepository(),
    ): Pair<CounterStore, FakeEncounterRepository> {
        val encounters = FakeEncounterRepository()
        return newCounterStore(
            encounterRepository = encounters,
            settingsRepository = settingsRepository,
            walkRepository = walkRepository,
        ) to encounters
    }

    @Test
    fun `tapping the chip stores the flag`() = runTest(mainDispatcher) {
        val settings = FakeSettingsRepository()
        val (store, _) = newStore(settingsRepository = settings)

        store.dispatch(CounterIntent.WalkingModeToggled(enabled = true))
        runCurrent()

        assertEquals(true, settings.walkingMode().first())
        assertEquals(true, store.state.value.walkingMode)
    }

    @Test
    fun `the chip follows a walk stopped from the notification, with no intent of its own`() =
        runTest(mainDispatcher) {
            val settings = FakeSettingsRepository()
            val (store, _) = newStore(settingsRepository = settings)
            store.dispatch(CounterIntent.WalkingModeToggled(enabled = true))
            runCurrent()

            settings.setWalkingMode(false)
            runCurrent()

            assertEquals(false, store.state.value.walkingMode)
        }

    @Test
    fun `a chip tap the store cannot write leaves it off rather than crashing`() =
        runTest(mainDispatcher) {
            val settings = FakeSettingsRepository(writesFail = true)
            val (store, _) = newStore(settingsRepository = settings)

            store.dispatch(CounterIntent.WalkingModeToggled(enabled = true))
            runCurrent()

            assertEquals(false, store.state.value.walkingMode)
        }

    @Test
    fun `walking mode survives the stats flow rebuilding the whole state`() = runTest(mainDispatcher) {
        val (store, repository) = newStore()
        store.dispatch(CounterIntent.WalkingModeToggled(enabled = true))
        runCurrent()

        repository.insert(externalEncounter(id = "a-cat"))
        runCurrent()

        assertEquals(true, store.state.value.walkingMode)
    }

    @Test
    fun `a walk on shows how long it has lasted`() = runTest(mainDispatcher) {
        val settings = FakeSettingsRepository().apply { setWalkingMode(true) }
        val walks = FakeWalkRepository().apply { startAt(CounterNow - 32.minutes) }

        val (store, _) = newStore(settingsRepository = settings, walkRepository = walks)

        assertEquals(
            CounterState(
                totalLabel = "0",
                count = 0,
                undoVisible = false,
                walkingMode = true,
                walkElapsedLabel = FakeDateTimeFormatter().duration(32.minutes),
            ),
            store.state.value,
        )
    }

    @Test
    fun `with no walk on the state carries no walk time`() = runTest(mainDispatcher) {
        val walks = FakeWalkRepository().apply { startAt(CounterNow - 32.minutes) }

        val (store, _) = newStore(walkRepository = walks)

        assertEquals(CounterState(totalLabel = "0", count = 0, undoVisible = false), store.state.value)
    }

    @Test
    fun `walking mode just turned on shows no time until its walk has started`() = runTest(mainDispatcher) {
        val settings = FakeSettingsRepository()
        val walks = FakeWalkRepository()
        val (store, _) = newStore(settingsRepository = settings, walkRepository = walks)

        settings.setWalkingMode(true)
        runCurrent()
        assertEquals(
            CounterState(totalLabel = "0", count = 0, undoVisible = false, walkingMode = true),
            store.state.value,
        )

        walks.startAt(CounterNow)
        runCurrent()
        assertEquals(Duration.ZERO.toString(), store.state.value.walkElapsedLabel)
    }

    @Test
    fun `walking mode turned off drops the time even before its walk has ended`() = runTest(mainDispatcher) {
        val settings = FakeSettingsRepository().apply { setWalkingMode(true) }
        val walks = FakeWalkRepository().apply { startAt(CounterNow - 5.minutes) }
        val (store, _) = newStore(settingsRepository = settings, walkRepository = walks)

        settings.setWalkingMode(false)
        runCurrent()

        assertEquals(false, store.state.value.walkingMode)
        assertNull(store.state.value.walkElapsedLabel)
    }

    @Test
    fun `the walk time survives the stats flow rebuilding the whole state`() = runTest(mainDispatcher) {
        val settings = FakeSettingsRepository().apply { setWalkingMode(true) }
        val walks = FakeWalkRepository().apply { startAt(CounterNow - 5.minutes) }
        val (store, repository) = newStore(settingsRepository = settings, walkRepository = walks)

        repository.insert(externalEncounter(id = "a-cat"))
        runCurrent()

        assertEquals(5.minutes.toString(), store.state.value.walkElapsedLabel)
    }

    @Test
    fun `nothing watches the walks while walking mode is off`() = runTest(mainDispatcher) {
        val settings = milestonesAlreadyCelebrated()
        val walks = FakeWalkRepository()
        newStore(settingsRepository = settings, walkRepository = walks)
        assertEquals(0, walks.watching)

        settings.setWalkingMode(true)
        runCurrent()

        assertEquals(1, walks.watching)
    }
}
