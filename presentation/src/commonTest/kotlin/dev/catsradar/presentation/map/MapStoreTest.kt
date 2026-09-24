package dev.catsradar.presentation.map

import app.cash.turbine.test
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.coat.CoatOption
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
        }

    @Test
    fun `tapping several cats asks to open their spot under the coats chosen at that moment`() =
        runTest(mainDispatcher) {
            repository.insert(located("a", minute = 0))
            repository.insert(located("b", minute = 5))
            val store = newStore()
            runCurrent()

            store.effects.test {
                store.dispatch(MapIntent.CatsTapped(listOf("a", "b", "a")))
                runCurrent()
                assertEquals(MapEffect.OpenSpot(setOf("a", "b"), coats = emptySet()), awaitItem())

                store.dispatch(MapIntent.CoatToggled(CoatOption.GINGER))
                store.dispatch(MapIntent.CatsTapped(listOf("a", "b")))
                runCurrent()
                assertEquals(MapEffect.OpenSpot(setOf("a", "b"), coats = setOf(CoatOption.GINGER)), awaitItem())
            }
        }

    @Test
    fun `focusing an outing shows it alone, and clearing it shows every cat again`() =
        runTest(mainDispatcher) {
            val first = located("first", minute = 0)
            val second = located("second", minute = 5).copy(lat = 41.40)
            repository.insert(first)
            repository.insert(second)
            repository.insert(located("other outing", minute = 180).copy(lat = 41.45))
            val store = newStore()
            runCurrent()

            store.dispatch(MapIntent.OutingFocused("second"))
            runCurrent()
            val focused = assertIs<MapState.Located>(store.state.value)
            assertEquals("first", focused.focus?.outingId)
            assertEquals(listOf("first", "second"), focused.points.map { it.id })

            store.dispatch(MapIntent.FocusCleared)
            runCurrent()
            val everyCat = assertIs<MapState.Located>(store.state.value)
            assertNull(everyCat.focus)
            assertEquals(3, everyCat.points.size)
        }

    @Test
    fun `a focus whose outing loses its last located cat is let go, and does not come back on its own`() =
        runTest(mainDispatcher) {
            val tally = encounterFixture("tally", BASE)
            val located = located("located", minute = 5)
            repository.insert(tally)
            repository.insert(located)
            repository.insert(located("other outing", minute = 180))
            val store = newStore()
            runCurrent()
            store.dispatch(MapIntent.OutingFocused("tally"))
            runCurrent()
            assertEquals("tally", assertIs<MapState.Located>(store.state.value).focus?.outingId)

            repository.update(located.copy(deletedAt = BASE + 1.hours))
            runCurrent()
            repository.update(tally.copy(lat = 41.40, lon = 2.18))
            runCurrent()

            assertNull(assertIs<MapState.Located>(store.state.value).focus)
        }

    @Test
    fun `coats are chosen one at a time and cleared back to every cat, and heat turns on and off`() =
        runTest(mainDispatcher) {
            repository.insert(located("a", minute = 0))
            val store = newStore()
            runCurrent()
            fun shown() = assertIs<MapState.Located>(store.state.value)

            store.dispatch(MapIntent.CoatToggled(CoatOption.GINGER))
            store.dispatch(MapIntent.CoatToggled(null))
            runCurrent()
            assertEquals(setOf(CoatOption.GINGER, null), shown().shownCoats)

            store.dispatch(MapIntent.CoatToggled(CoatOption.GINGER))
            runCurrent()
            assertEquals(setOf<CoatOption?>(null), shown().shownCoats)

            store.dispatch(MapIntent.CoatFilterCleared)
            store.dispatch(MapIntent.HeatToggled)
            runCurrent()
            assertEquals(emptySet(), shown().shownCoats)
            assertEquals(true, shown().heat)

            store.dispatch(MapIntent.HeatToggled)
            runCurrent()
            assertEquals(false, shown().heat)
        }

    // Floating-point padding: compare the area to a millionth of a degree.
    private fun MapState.roundedArea(): MapState = when (this) {
        is MapState.Located -> copy(area = area.run { MapArea(south.r(), west.r(), north.r(), east.r()) })
        else -> this
    }

    private fun Double.r() = kotlin.math.round(this * 1e6) / 1e6

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
    }
}
