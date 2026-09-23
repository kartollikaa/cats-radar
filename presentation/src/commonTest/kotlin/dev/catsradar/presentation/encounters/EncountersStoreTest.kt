package dev.catsradar.presentation.encounters

import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeEncounterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EncountersStoreTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newStore(repository: FakeEncounterRepository): EncountersStore = EncountersStore(
        observeEncounters = ObserveEncounters(repository),
        stateMapper = EncountersStateMapper(FakeDateTimeFormatter(), FakePhotoStorage()),
        clock = FakeClock(Instant.parse("2026-09-22T12:00:00Z")),
        timeZone = TimeZone.UTC,
    )

    @Test
    fun `initial state is empty before any encounter is observed`() = runTest(mainDispatcher) {
        val store = newStore(FakeEncounterRepository())
        runCurrent()

        assertTrue(store.state.value.isEmpty)
    }

    @Test
    fun `a logged encounter appears as a row once the repository emits it`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = newStore(repository)
        runCurrent()

        repository.insert(encounterFixture("1", Instant.parse("2026-09-22T10:00:00Z")))
        runCurrent()

        assertEquals(false, store.state.value.isEmpty)
        assertEquals(1, store.state.value.rows.count { it is EncounterGridRow.Cards })
    }
}
