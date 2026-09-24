package dev.catsradar.presentation.statistics

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.ObserveWalkStats
import dev.catsradar.domain.usecase.ObserveWalkTracks
import dev.catsradar.presentation.StoredWalkRepository
import dev.catsradar.presentation.counter.FakeClock
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
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

    private fun newStore(walkRepository: StoredWalkRepository) = StatisticsStore(
        observeStats = ObserveStats(encounterRepository, FakeClock(BASE + 2.hours), TimeZone.UTC, ticks = flowOf(Unit)),
        observeWalkStats = ObserveWalkStats(encounterRepository, ObserveWalkTracks(walkRepository)),
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
    fun `no stored walk leaves the walk rows out`() = runTest(mainDispatcher) {
        val store = newStore(StoredWalkRepository())
        runCurrent()

        assertNull(store.state.value.walked)
    }

    private companion object {
        val BASE = Instant.parse("2026-09-22T10:00:00Z")
    }
}
