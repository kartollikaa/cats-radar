package dev.catsradar.presentation.detail

import app.cash.turbine.test
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EncounterDetailStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()
    private val clock = FakeClock(NOW)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an existing encounter renders as loaded with its fields`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED, locationSource = LocationSource.NONE))
        val store = newStore()
        runCurrent()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(OCCURRED.toString(), state.timeLabel)
    }

    @Test
    fun `an id nobody has ever seen renders as missing, without throwing`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()

        assertEquals(EncounterDetailState.Missing, store.state.value)
    }

    @Test
    fun `an encounter soft-deleted elsewhere is never presented as live`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED, deletedAt = NOW))
        val store = newStore()
        runCurrent()

        assertEquals(EncounterDetailState.Missing, store.state.value)
    }

    @Test
    fun `an encounter deleted elsewhere while on screen turns the screen to missing`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        assertIs<EncounterDetailState.Loaded>(store.state.value)

        repository.softDelete(ID, NOW)
        runCurrent()

        assertEquals(EncounterDetailState.Missing, store.state.value)
    }

    @Test
    fun `delete soft-deletes once and shows the undo affordance`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        assertEquals(listOf(ID), repository.softDeletedIds)
        assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)
        assertEquals(null, repository.observeById(ID).value())
    }

    @Test
    fun `pressing delete twice soft-deletes exactly once`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.DeleteClicked)
        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        assertEquals(listOf(ID), repository.softDeletedIds)
    }

    @Test
    fun `undo within the window clears the deletion and the encounter is live again`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        advanceTimeBy(Tuning.UNDO_VISIBLE - 1.seconds)
        store.dispatch(EncounterDetailIntent.UndoClicked)
        runCurrent()

        assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(null, repository.observeById(ID).value()?.deletedAt)
        assertEquals(1, repository.observeAll().value().size)
    }

    @Test
    fun `the undo affordance disappears when the window closes and the screen navigates back once`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            val store = newStore()
            runCurrent()

            store.effects.test {
                store.dispatch(EncounterDetailIntent.DeleteClicked)
                runCurrent()
                assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

                advanceTimeBy(Tuning.UNDO_VISIBLE - 1.milliseconds)
                runCurrent()
                assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

                advanceTimeBy(1.milliseconds)
                runCurrent()
                assertEquals(EncounterDetailState.Deleted(undoVisible = false), store.state.value)
                assertEquals(EncounterDetailEffect.NavigateBack, awaitItem())

                advanceTimeBy(Tuning.UNDO_VISIBLE * 3)
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun `undo after the window closed is a no-op and the deletion stands`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()
        advanceTimeBy(Tuning.UNDO_VISIBLE + 1.milliseconds)
        runCurrent()

        store.dispatch(EncounterDetailIntent.UndoClicked)
        runCurrent()

        assertEquals(EncounterDetailState.Deleted(undoVisible = false), store.state.value)
        assertEquals(null, repository.observeById(ID).value())
    }

    @Test
    fun `delete on a missing encounter does nothing`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()

        assertEquals(EncounterDetailState.Missing, store.state.value)
        assertFalse(ID in repository.softDeletedIds)
    }

    @Test
    fun `a failed delete restores the loaded state instead of showing a deletion that did not happen`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            repository.softDeleteShouldThrow = IllegalStateException("disk full")
            val store = newStore()
            runCurrent()

            store.dispatch(EncounterDetailIntent.DeleteClicked)
            runCurrent()

            assertIs<EncounterDetailState.Loaded>(store.state.value)
            store.effects.test {
                advanceTimeBy(Tuning.UNDO_VISIBLE * 2)
                runCurrent()
                expectNoEvents()
            }
        }

    private fun TestScope.newStore(): EncounterDetailStore = EncounterDetailStore(
        encounterId = ID,
        observeEncounter = ObserveEncounter(repository),
        deleteEncounter = DeleteEncounter(repository, clock),
        undoDelete = UndoDelete(repository),
        stateMapper = EncounterDetailStateMapper(FakeDateTimeFormatter()),
        clock = clock,
        timeZone = TimeZone.UTC,
    )

    private suspend fun <T> Flow<T>.value(): T = first()

    private companion object {
        const val ID = "cat-1"
        val NOW = Instant.parse("2026-09-22T12:00:00Z")
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
