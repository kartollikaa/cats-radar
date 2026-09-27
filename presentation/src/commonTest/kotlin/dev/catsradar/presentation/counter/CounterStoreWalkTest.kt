package dev.catsradar.presentation.counter

import app.cash.turbine.test
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
    ): Pair<CounterStore, FakeEncounterRepository> {
        val encounters = FakeEncounterRepository()
        return newCounterStore(
            encounterRepository = encounters,
            settingsRepository = settingsRepository,
        ) to encounters
    }

    @Test
    fun `a press let go before the hold is up raises the hint`() = runTest(mainDispatcher) {
        val (store, _) = newStore()

        store.effects.test {
            store.dispatch(CounterIntent.WalkHoldReleased)
            runCurrent()

            assertEquals(CounterEffect.WalkNeedsHold, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
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
}
