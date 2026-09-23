package dev.catsradar.presentation.map

import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MapStoreTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newStore(repository: FakeEncounterRepository) =
        MapStore(observeEncounters = ObserveEncounters(repository), stateMapper = MapStateMapper())

    @Test
    fun `the map is loading until the cats have been read, then empty with none located`() =
        runTest(mainDispatcher) {
            val store = newStore(FakeEncounterRepository())
            assertEquals(MapState.Loading, store.state.value)

            runCurrent()

            assertEquals(MapState.Empty, store.state.value)
        }

    @Test
    fun `a cat that gets a location appears on the map`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val store = newStore(repository)
        runCurrent()

        repository.insert(encounterFixture("1", Instant.parse("2026-09-22T10:00:00Z")).copy(lat = 41.39, lon = 2.17))
        runCurrent()

        assertEquals(listOf("1"), assertIs<MapState.Located>(store.state.value).points.map { it.id })
    }
}
