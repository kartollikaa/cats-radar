package dev.catsradar.domain.usecase

import app.cash.turbine.test
import dev.catsradar.domain.geo.trackLengthMeters
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import dev.catsradar.domain.stats.WalkStats
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeWalkRepository
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val START = Instant.parse("2026-09-24T10:00:00Z")

// One degree of latitude is about 111.195 km on the mean Earth radius the distance uses.
private fun point(walkId: String, at: Instant, km: Double) = TrackPoint(walkId, at, 41.39 + km / 111.195, 2.17, 5f)

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveWalkStatsTest {

    private val walkRepository = FakeWalkRepository()
    private val encounterRepository = FakeEncounterRepository()

    private val walk = Walk("w", START, START + 1.hours, "device", START, START + 1.hours)

    private suspend fun storeKilometreWalk() {
        walkRepository.upsert(walk)
        walkRepository.appendPoints(
            listOf(point(walk.id, START, km = 0.0), point(walk.id, START + 1.minutes, km = 1.0)),
        )
    }

    private fun TestScope.observe(
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): Flow<WalkStats> =
        ObserveWalkStats(ObserveWalkTracks(walkRepository), dispatcher)(encounterRepository.observeAll())

    @Test
    fun `cats per km reflects the walk's route, and a new point widens it`() = runTest {
        storeKilometreWalk()
        encounterRepository.insert(encounterFixture("a", START + 30.minutes))

        observe().test {
            val firstMeters = trackLengthMeters(walkRepository.points())
            assertEquals(WalkStats(walkedMeters = firstMeters, catsPerKm = 1 / (firstMeters / 1000)), awaitItem())

            walkRepository.appendPoints(listOf(point(walk.id, START + 2.minutes, km = 2.0)))

            val secondMeters = trackLengthMeters(walkRepository.points())
            assertEquals(WalkStats(walkedMeters = secondMeters, catsPerKm = 1 / (secondMeters / 1000)), awaitItem())
            assertTrue(secondMeters > firstMeters)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a cat logged during a measured walk raises its cats per km`() = runTest {
        storeKilometreWalk()
        encounterRepository.insert(encounterFixture("a", START + 30.minutes))
        val kilometres = trackLengthMeters(walkRepository.points()) / 1000

        observe().test {
            assertEquals(1 / kilometres, awaitItem().catsPerKm)

            encounterRepository.insert(encounterFixture("b", START + 40.minutes))

            assertEquals(2 / kilometres, awaitItem().catsPerKm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `walk stats are worked out on the injected dispatcher, not the collector's`() = runTest {
        storeKilometreWalk()
        val compute = StandardTestDispatcher(testScheduler, name = "compute")
        val witness = WitnessWalkRepository(walkRepository)
        val stats = ObserveWalkStats(ObserveWalkTracks(witness), compute)(encounterRepository.observeAll())

        stats.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertSame(compute, witness.pointsCollectedOn)
    }

    @Test
    fun `a collector still busy with one result gets only the latest of those worked out meanwhile`() = runTest {
        storeKilometreWalk()
        val seen = mutableListOf<WalkStats>()
        val collecting = launch {
            observe().collect {
                seen += it
                delay(1.minutes)
            }
        }
        runCurrent()

        walkRepository.appendPoints(listOf(point(walk.id, START + 2.minutes, km = 2.0)))
        runCurrent()
        walkRepository.appendPoints(listOf(point(walk.id, START + 3.minutes, km = 3.0)))
        runCurrent()
        advanceTimeBy(1.minutes)
        runCurrent()
        collecting.cancel()

        assertEquals(listOf(1.0, 3.0), seen.map { round(it.walkedMeters / 1000) })
    }
}

/** Delegates to [delegate], noting the dispatcher [observeEveryPoint] was collected on. */
private class WitnessWalkRepository(private val delegate: WalkRepository) : WalkRepository by delegate {

    var pointsCollectedOn: ContinuationInterceptor? = null
        private set

    override fun observeEveryPoint(): Flow<List<TrackPoint>> =
        delegate.observeEveryPoint().onStart { pointsCollectedOn = currentCoroutineContext()[ContinuationInterceptor] }
}
