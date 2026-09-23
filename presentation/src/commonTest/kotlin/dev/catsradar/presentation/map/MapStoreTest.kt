package dev.catsradar.presentation.map

import app.cash.turbine.test
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.EncountersStateMapper
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MapStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val encountersMapper = EncountersStateMapper(FakeDateTimeFormatter(), FakePhotoStorage())
    private val repository = FakeEncounterRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newStore() = MapStore(
        observeEncounters = ObserveEncounters(repository),
        stateMapper = MapStateMapper(encountersMapper),
        clock = FakeClock(BASE + 3.hours),
        timeZone = TimeZone.UTC,
    )

    private fun located(id: String, minute: Int) =
        encounterFixture(id, BASE + minute.minutes).copy(lat = 41.39, lon = 2.17)

    private fun spotOf(vararg cats: Encounter) =
        MapSpot(catCount = cats.size, rows = encountersMapper.map(cats.toList(), TODAY, grid = false).rows)

    private fun MapState.spot(): MapSpot? = assertIs<MapState.Located>(this).spot

    @Test
    fun `the map is loading until the cats have been read, then empty with none located`() =
        runTest(mainDispatcher) {
            val store = newStore()
            assertEquals(MapState.Loading, store.state.value)

            runCurrent()

            assertEquals(MapState.Empty, store.state.value)
        }

    @Test
    fun `a cat that gets its location afterwards appears on the map`() = runTest(mainDispatcher) {
        val cat = encounterFixture("1", BASE)
        repository.insert(cat)
        val store = newStore()
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

    @Test
    fun `tapping one cat opens it, even when the tap reports it twice, and tapping nothing does nothing`() =
        runTest(mainDispatcher) {
            repository.insert(located("a", minute = 0))
            val store = newStore()
            runCurrent()

            store.effects.test {
                store.dispatch(MapIntent.CatsTapped(emptyList()))
                runCurrent()
                expectNoEvents()

                store.dispatch(MapIntent.CatsTapped(listOf("a")))
                runCurrent()
                assertEquals(MapEffect.OpenCat("a"), awaitItem())

                store.dispatch(MapIntent.CatsTapped(listOf("a", "a")))
                runCurrent()
                assertEquals(MapEffect.OpenCat("a"), awaitItem())
            }
            assertNull(store.state.value.spot())
        }

    @Test
    fun `tapping several cats lists exactly those, grouped as in the list, until dismissed`() =
        runTest(mainDispatcher) {
            val a = located("a", minute = 0)
            val b = located("b", minute = 5)
            repository.insert(a)
            repository.insert(b)
            repository.insert(located("elsewhere", minute = 10))
            val store = newStore()
            runCurrent()

            store.dispatch(MapIntent.CatsTapped(listOf("a", "b")))
            runCurrent()
            assertEquals(spotOf(a, b), store.state.value.spot())

            store.dispatch(MapIntent.SpotDismissed)
            runCurrent()
            assertNull(store.state.value.spot())
        }

    @Test
    fun `a cat deleted while its spot is open drops out of the list`() = runTest(mainDispatcher) {
        val a = located("a", minute = 0)
        val b = located("b", minute = 5)
        repository.insert(a)
        repository.insert(b)
        val store = newStore()
        runCurrent()
        store.dispatch(MapIntent.CatsTapped(listOf("a", "b")))
        runCurrent()

        repository.update(b.copy(deletedAt = BASE + 1.hours))
        runCurrent()

        assertEquals(spotOf(a), store.state.value.spot())
    }

    // Floating-point padding: compare the area to a millionth of a degree.
    private fun MapState.roundedArea(): MapState = when (this) {
        is MapState.Located -> copy(area = area.run { MapArea(south.r(), west.r(), north.r(), east.r()) })
        else -> this
    }

    private fun Double.r() = kotlin.math.round(this * 1e6) / 1e6

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
        val TODAY = LocalDate(2026, 9, 22)
    }
}
