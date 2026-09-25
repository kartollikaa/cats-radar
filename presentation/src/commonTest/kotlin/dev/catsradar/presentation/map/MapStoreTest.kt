package dev.catsradar.presentation.map

import app.cash.turbine.test
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.ObserveWalkTracks
import dev.catsradar.presentation.DelayedWalkRepository
import dev.catsradar.presentation.StoredWalkRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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

    private fun newStore(walks: WalkRepository = StoredWalkRepository()) = MapStore(
        observeEncounters = ObserveEncounters(repository),
        observeWalkTracks = ObserveWalkTracks(walks),
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
    fun `focusing an outing whose walk has a track draws it instead of the line joining its cats`() =
        runTest(mainDispatcher) {
            val first = located("first", minute = 0)
            val second = located("second", minute = 5).copy(lat = 41.40)
            repository.insert(first)
            repository.insert(second)
            val walk = Walk(
                id = "walk-1",
                startedAt = BASE,
                endedAt = BASE + 10.minutes,
                deviceId = "device",
                createdAt = BASE,
                updatedAt = BASE,
            )
            val points = listOf(
                TrackPoint(walkId = "walk-1", at = BASE, lat = 41.388, lon = 2.168, accuracyMeters = 5f),
                TrackPoint(walkId = "walk-1", at = BASE + 1.minutes, lat = 41.395, lon = 2.175, accuracyMeters = 5f),
            )
            val store = newStore(StoredWalkRepository(walks = listOf(walk), points = points))
            runCurrent()

            store.dispatch(MapIntent.OutingFocused("second"))
            runCurrent()

            val focused = assertIs<MapState.Located>(store.state.value)
            assertEquals(
                persistentListOf(MapLine(persistentListOf(MapPosition(41.388, 2.168), MapPosition(41.395, 2.175)))),
                focused.focus?.lines,
            )
        }

    @Test
    fun `a focus never reaches state paired with the cat line once its walk's track arrives`() =
        runTest(mainDispatcher) {
            val first = located("first", minute = 0)
            val second = located("second", minute = 5).copy(lat = 41.40)
            repository.insert(first)
            repository.insert(second)
            val walk = Walk(
                id = "walk-1",
                startedAt = BASE,
                endedAt = BASE + 10.minutes,
                deviceId = "device",
                createdAt = BASE,
                updatedAt = BASE,
            )
            val points = listOf(
                TrackPoint(walkId = "walk-1", at = BASE, lat = 41.388, lon = 2.168, accuracyMeters = 5f),
                TrackPoint(walkId = "walk-1", at = BASE + 1.minutes, lat = 41.395, lon = 2.175, accuracyMeters = 5f),
            )
            val store = newStore(DelayedWalkRepository(walks = listOf(walk), points = points))
            runCurrent()

            val seen = mutableListOf<MapState>()
            val collecting = launch { store.state.toList(seen) }
            store.dispatch(MapIntent.OutingFocused("second"))
            advanceTimeBy(2.seconds)
            runCurrent()
            collecting.cancel()

            val focusedLines = seen.filterIsInstance<MapState.Located>().mapNotNull { it.focus?.lines }
            val catLine = persistentListOf(
                MapLine(persistentListOf(MapPosition(41.39, 2.17), MapPosition(41.40, 2.17))),
            )
            val trackLine = persistentListOf(
                MapLine(persistentListOf(MapPosition(41.388, 2.168), MapPosition(41.395, 2.175))),
            )
            assertTrue(catLine !in focusedLines, "a focused state carried the cat line while the walk had a track")
            assertTrue(trackLine in focusedLines, "the walk's track never reached a focused state")
        }

    @Test
    fun `switching focus while its tracks are still pending does not let a stale pairing clear the new one`() =
        runTest(mainDispatcher) {
            val x1 = located("x1", minute = 0)
            val x2 = located("x2", minute = 5).copy(lat = 41.40)
            val y1 = located("y1", minute = 180).copy(lat = 41.45)
            repository.insert(x1)
            repository.insert(x2)
            repository.insert(y1)
            val store = newStore(DelayedWalkRepository())
            runCurrent()

            store.dispatch(MapIntent.OutingFocused("x1"))
            advanceTimeBy(2.seconds)
            runCurrent()
            assertEquals("x1", assertIs<MapState.Located>(store.state.value).focus?.outingId)

            store.dispatch(MapIntent.OutingFocused("y1"))
            runCurrent() // y1's own tracks have not answered yet; the cached pairing still names x1.

            repository.update(x1.copy(deletedAt = BASE + 1.hours))
            repository.update(x2.copy(deletedAt = BASE + 1.hours))
            runCurrent() // x1 no longer matches while the stale pairing is live: must not clear y1.

            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals("y1", assertIs<MapState.Located>(store.state.value).focus?.outingId)
        }

    @Test
    fun `an unfocused store never collects walk tracks, and focusing an outing starts collecting them`() =
        runTest(mainDispatcher) {
            val walk = Walk(
                id = "walk-1",
                startedAt = BASE,
                endedAt = BASE + 10.minutes,
                deviceId = "device",
                createdAt = BASE,
                updatedAt = BASE,
            )
            val points = listOf(
                TrackPoint(walkId = "walk-1", at = BASE, lat = 41.388, lon = 2.168, accuracyMeters = 5f),
                TrackPoint(walkId = "walk-1", at = BASE + 1.minutes, lat = 41.395, lon = 2.175, accuracyMeters = 5f),
            )
            val walks = CountingWalkRepository(walks = listOf(walk), points = points)
            repository.insert(located("first", minute = 0))
            val store = newStore(walks)
            runCurrent()
            assertEquals(0, walks.everyPointCollections)

            store.dispatch(MapIntent.OutingFocused("first"))
            runCurrent()

            assertEquals(1, walks.everyPointCollections)
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

/** A read-only stand-in that counts how many times [observeEveryPoint] is collected. */
private class CountingWalkRepository(
    walks: List<Walk> = emptyList(),
    points: List<TrackPoint> = emptyList(),
    private val delegate: WalkRepository = StoredWalkRepository(walks, points),
) : WalkRepository by delegate {

    var everyPointCollections = 0
        private set

    override fun observeEveryPoint(): Flow<List<TrackPoint>> =
        delegate.observeEveryPoint().onStart { everyPointCollections++ }
}
