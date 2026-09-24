package dev.catsradar.presentation.map

import app.cash.turbine.test
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.EncountersStateMapper
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MapSpotStoreTest {

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

    private fun newStore(catIds: Set<String>, coats: Set<CoatOption?> = emptySet()) = MapSpotStore(
        catIds = catIds,
        coats = coats,
        observeEncounters = ObserveEncounters(repository),
        stateMapper = MapSpotStateMapper(encountersMapper),
        clock = FakeClock(BASE + 3.hours),
        timeZone = TimeZone.UTC,
    )

    private fun located(id: String, minute: Int, coat: CatCoat? = null) =
        encounterFixture(id, BASE + minute.minutes).copy(lat = 41.39, lon = 2.17, coat = coat)

    private fun listed(vararg cats: Encounter) = MapSpotState.Listed(
        catCount = cats.size,
        rows = encountersMapper.map(cats.toList(), TODAY, grid = false).rows,
    )

    @Test
    fun `the list is loading until the cats have been read, then lists exactly the spot's cats`() =
        runTest(mainDispatcher) {
            val a = located("a", minute = 0)
            val b = located("b", minute = 5)
            repository.insert(a)
            repository.insert(b)
            repository.insert(located("elsewhere", minute = 10))
            val store = newStore(setOf("a", "b"))
            assertEquals(MapSpotState.Loading, store.state.value)

            runCurrent()

            assertEquals(listed(a, b), store.state.value)
        }

    @Test
    fun `a cat deleted while its list is open drops out of it`() = runTest(mainDispatcher) {
        val a = located("a", minute = 0)
        val b = located("b", minute = 5)
        repository.insert(a)
        repository.insert(b)
        val store = newStore(setOf("a", "b"))
        runCurrent()

        repository.update(b.copy(deletedAt = BASE + 1.hours))
        runCurrent()

        assertEquals(listed(a), store.state.value)
    }

    @Test
    fun `a cat whose coat stops being a shown one drops out of the list`() = runTest(mainDispatcher) {
        val ginger = located("ginger", minute = 0, CatCoat.GINGER)
        val other = located("other", minute = 5, CatCoat.GINGER)
        repository.insert(ginger)
        repository.insert(other)
        val store = newStore(setOf("ginger", "other"), coats = setOf(CoatOption.GINGER))
        runCurrent()

        repository.update(other.copy(coat = CatCoat.BLACK))
        runCurrent()

        assertEquals(listed(ginger), store.state.value)
    }

    @Test
    fun `a list whose cats are all deleted asks to close once, and restoring one does not bring it back`() =
        runTest(mainDispatcher) {
            val a = located("a", minute = 0)
            val b = located("b", minute = 5)
            repository.insert(a)
            repository.insert(b)
            val store = newStore(setOf("a", "b"))
            runCurrent()

            store.effects.test {
                repository.update(a.copy(deletedAt = BASE + 1.hours))
                repository.update(b.copy(deletedAt = BASE + 1.hours))
                runCurrent()
                assertEquals(MapSpotEffect.Close, awaitItem())
                val closed = store.state.value

                repository.update(a)
                runCurrent()
                expectNoEvents()
                assertEquals(closed, store.state.value)
            }
        }

    @Test
    fun `a row opens its cat and an outing header's map button focuses that outing`() = runTest(mainDispatcher) {
        repository.insert(located("a", minute = 0))
        repository.insert(located("b", minute = 5))
        val store = newStore(setOf("a", "b"))
        runCurrent()

        store.effects.test {
            store.dispatch(MapSpotIntent.CatClicked("b"))
            runCurrent()
            assertEquals(MapSpotEffect.OpenCat("b"), awaitItem())

            store.dispatch(MapSpotIntent.OutingMapClicked("b"))
            runCurrent()
            assertEquals(MapSpotEffect.FocusOuting("b"), awaitItem())
        }
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
        val TODAY = LocalDate(2026, 9, 22)
    }
}
