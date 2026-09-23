package dev.catsradar.presentation.map

import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.collections.immutable.persistentListOf
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
    fun `a cat that gets its location afterwards appears on the map`() = runTest(mainDispatcher) {
        val repository = FakeEncounterRepository()
        val cat = encounterFixture("1", Instant.parse("2026-09-22T10:00:00Z"))
        repository.insert(cat)
        val store = newStore(repository)
        runCurrent()
        assertEquals(MapState.Empty, store.state.value)

        repository.update(cat.copy(lat = 41.39, lon = 2.17))
        runCurrent()

        assertEquals(
            MapState.Located(
                points = persistentListOf(MapPoint("1", 41.39, 2.17, coat = null)),
                area = MapArea(south = 41.385, west = 2.165, north = 41.395, east = 2.175),
            ),
            store.state.value.roundedArea(),
        )
    }

    // Floating-point padding: compare the area to a millionth of a degree.
    private fun MapState.roundedArea(): MapState = when (this) {
        is MapState.Located -> copy(area = area.run { MapArea(south.r(), west.r(), north.r(), east.r()) })
        else -> this
    }

    private fun Double.r() = kotlin.math.round(this * 1e6) / 1e6
}
