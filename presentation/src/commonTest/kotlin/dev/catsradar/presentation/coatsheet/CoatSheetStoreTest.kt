package dev.catsradar.presentation.coatsheet

import app.cash.turbine.test
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.presentation.NoAnalytics
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CoatSheetStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the sheet opens on the cat's coat, with the hint to pick another`() = runTest(mainDispatcher) {
        repository.insert(cat(CatCoat.GINGER))
        val store = newStore()
        assertEquals(CoatSheetState.Loading, store.state.value)

        runCurrent()

        assertEquals(CoatSheetState.Open(CoatOption.GINGER, CoatSheetHint.PICK_ANOTHER), store.state.value)
    }

    @Test
    fun `a cat with no coat opens with the hint to tap the one that fits`() = runTest(mainDispatcher) {
        repository.insert(cat(coat = null))
        val store = newStore()
        runCurrent()

        assertEquals(CoatSheetState.Open(coat = null, hint = CoatSheetHint.TAP_ONE), store.state.value)
    }

    @Test
    fun `a tap on a coat sets it and closes the sheet`() = runTest(mainDispatcher) {
        repository.insert(cat(CatCoat.GINGER))
        repository.insert(encounterFixture(OTHER, OCCURRED + 5.minutes).copy(coat = CatCoat.WHITE))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(CoatSheetIntent.CoatClicked(CoatOption.BLACK))
            assertEquals(CoatSheetEffect.Close, awaitItem())
        }

        assertEquals(CatCoat.BLACK, coatOf(ID))
        assertEquals(CatCoat.WHITE, coatOf(OTHER))
    }

    @Test
    fun `no coat clears it and closes the sheet`() = runTest(mainDispatcher) {
        repository.insert(cat(CatCoat.GINGER))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(CoatSheetIntent.NoCoatClicked)
            assertEquals(CoatSheetEffect.Close, awaitItem())
        }

        assertEquals(null, coatOf(ID))
    }

    @Test
    fun `a tap on the ringed coat keeps it and closes the sheet`() = runTest(mainDispatcher) {
        repository.insert(cat(CatCoat.GINGER))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(CoatSheetIntent.CoatClicked(CoatOption.GINGER))
            assertEquals(CoatSheetEffect.Close, awaitItem())
        }

        assertEquals(CatCoat.GINGER, coatOf(ID))
    }

    @Test
    fun `only the first answer counts`() = runTest(mainDispatcher) {
        repository.insert(cat(CatCoat.GINGER))
        val store = newStore()
        runCurrent()
        val firstWrite = CompletableDeferred<Unit>()
        repository.setCoatGate = firstWrite

        store.effects.test {
            store.dispatch(CoatSheetIntent.CoatClicked(CoatOption.BLACK))
            store.dispatch(CoatSheetIntent.NoCoatClicked)
            runCurrent()
            firstWrite.complete(Unit)
            assertEquals(CoatSheetEffect.Close, awaitItem())
            runCurrent()
            expectNoEvents()
        }

        assertEquals(CatCoat.BLACK, coatOf(ID))
    }

    @Test
    fun `a failed write closes the sheet and leaves the coat as it was`() = runTest(mainDispatcher) {
        repository.insert(cat(CatCoat.GINGER))
        val store = newStore()
        runCurrent()
        repository.setCoatShouldThrow = IllegalStateException("disk full")

        store.effects.test {
            store.dispatch(CoatSheetIntent.CoatClicked(CoatOption.BLACK))
            assertEquals(CoatSheetEffect.Close, awaitItem())
        }

        assertEquals(CatCoat.GINGER, coatOf(ID))
    }

    @Test
    fun `a cat that stops being live closes the sheet, and bringing it back does not reopen it`() =
        runTest(mainDispatcher) {
            repository.insert(cat(CatCoat.GINGER))
            val store = newStore()
            runCurrent()

            store.effects.test {
                repository.softDelete(ID, OCCURRED + 1.minutes)
                runCurrent()
                assertEquals(CoatSheetEffect.Close, awaitItem())

                repository.undoDelete(ID)
                runCurrent()
                expectNoEvents()
            }
            assertEquals(CoatSheetState.Open(CoatOption.GINGER, CoatSheetHint.PICK_ANOTHER), store.state.value)
        }

    @Test
    fun `a sheet dismissed without an answer writes nothing`() = runTest(mainDispatcher) {
        repository.insert(cat(CatCoat.GINGER))
        val before = repository.observeById(ID).first()
        val store = newStore()

        store.effects.test {
            runCurrent()
            expectNoEvents()
        }

        assertEquals(before, repository.observeById(ID).first())
    }

    private fun newStore() = CoatSheetStore(
        catId = ID,
        observeEncounter = ObserveEncounter(repository),
        setCoat = SetCoat(repository, FakeClock(OCCURRED + 1.minutes), analytics = NoAnalytics),
        stateMapper = CoatSheetStateMapper(),
    )

    private fun cat(coat: CatCoat?) = encounterFixture(ID, OCCURRED).copy(coat = coat)

    private suspend fun coatOf(id: String): CatCoat? = repository.observeById(id).first()?.coat

    private companion object {
        const val ID = "cat-1"
        const val OTHER = "cat-2"
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
