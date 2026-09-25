package dev.catsradar.presentation.statistics

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.WalkRepository
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.ObserveWalkStats
import dev.catsradar.domain.usecase.ObserveWalkTracks
import dev.catsradar.presentation.CountingEncounterRepository
import dev.catsradar.presentation.DelayedWalkRepository
import dev.catsradar.presentation.StoredWalkRepository
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val encounterRepository = FakeEncounterRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newStore(
        walkRepository: WalkRepository,
        encounters: EncounterRepository = encounterRepository,
    ) = StatisticsStore(
        observeEncounters = ObserveEncounters(encounters),
        observeStats = ObserveStats(encounters, FakeClock(BASE + 2.hours), TimeZone.UTC, ticks = flowOf(Unit)),
        observeWalkStats = ObserveWalkStats(ObserveWalkTracks(walkRepository), mainDispatcher),
        stateMapper = StatisticsStateMapper(FakeDateTimeFormatter()),
    )

    @Test
    fun `a stored walk's route shows the distance walked, past a thousand metres in kilometres`() =
        runTest(mainDispatcher) {
            encounterRepository.insert(encounterFixture("cat-1", BASE + 10.minutes))
            val walk = Walk(
                id = "walk-1",
                startedAt = BASE,
                endedAt = BASE + 1.hours,
                deviceId = "device-1",
                createdAt = BASE,
                updatedAt = BASE + 1.hours,
            )
            val points = listOf(
                TrackPoint(walkId = "walk-1", at = BASE, lat = 41.390, lon = 2.170, accuracyMeters = 5f),
                TrackPoint(walkId = "walk-1", at = BASE + 30.minutes, lat = 41.408, lon = 2.170, accuracyMeters = 5f),
            )
            val store = newStore(StoredWalkRepository(walks = listOf(walk), points = points))
            runCurrent()

            assertEquals(DistanceState("2.0", DistanceUnit.KILOMETERS), store.state.value.walked?.distance)
        }

    @Test
    fun `stats show as soon as they're ready, with the walk rows following once the tracks answer`() =
        runTest(mainDispatcher) {
            encounterRepository.insert(encounterFixture("cat-1", BASE + 10.minutes))
            val walk = Walk(
                id = "walk-1",
                startedAt = BASE,
                endedAt = BASE + 1.hours,
                deviceId = "device-1",
                createdAt = BASE,
                updatedAt = BASE + 1.hours,
            )
            val points = listOf(
                TrackPoint(walkId = "walk-1", at = BASE, lat = 41.390, lon = 2.170, accuracyMeters = 5f),
                TrackPoint(walkId = "walk-1", at = BASE + 30.minutes, lat = 41.408, lon = 2.170, accuracyMeters = 5f),
            )
            val store = newStore(DelayedWalkRepository(walks = listOf(walk), points = points))
            runCurrent()

            assertTrue(store.state.value.hasAnyCats)
            assertNull(store.state.value.walked)

            advanceTimeBy(2.seconds)
            runCurrent()

            assertEquals(DistanceState("2.0", DistanceUnit.KILOMETERS), store.state.value.walked?.distance)
        }

    @Test
    fun `no stored walk leaves the walk rows out`() = runTest(mainDispatcher) {
        val store = newStore(StoredWalkRepository())
        runCurrent()

        assertNull(store.state.value.walked)
    }

    @Test
    fun `the screen reads the cats once for both the stats and the walk rows`() = runTest(mainDispatcher) {
        val encounters = CountingEncounterRepository(encounterRepository)
        encounters.insert(encounterFixture("cat-1", BASE + 10.minutes))
        val walk = Walk(
            id = "walk-1",
            startedAt = BASE,
            endedAt = BASE + 1.hours,
            deviceId = "device-1",
            createdAt = BASE,
            updatedAt = BASE + 1.hours,
        )
        val points = listOf(
            TrackPoint(walkId = "walk-1", at = BASE, lat = 41.390, lon = 2.170, accuracyMeters = 5f),
            TrackPoint(walkId = "walk-1", at = BASE + 30.minutes, lat = 41.408, lon = 2.170, accuracyMeters = 5f),
        )

        val store = newStore(StoredWalkRepository(walks = listOf(walk), points = points), encounters)
        runCurrent()

        assertTrue(store.state.value.hasAnyCats)
        assertEquals(
            WalkedState(DistanceState("2.0", DistanceUnit.KILOMETERS), catsPerKm = "0.5"),
            store.state.value.walked,
        )
        assertEquals(1, encounters.everyCollection)
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
    }
}
