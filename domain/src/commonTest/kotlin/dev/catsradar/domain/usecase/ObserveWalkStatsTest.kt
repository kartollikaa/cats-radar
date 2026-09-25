package dev.catsradar.domain.usecase

import app.cash.turbine.test
import dev.catsradar.domain.geo.trackLengthMeters
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.stats.WalkStats
import dev.catsradar.domain.testing.FakeEncounterRepository
import dev.catsradar.domain.testing.FakeWalkRepository
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val START = Instant.parse("2026-09-24T10:00:00Z")

// One degree of latitude is about 111.195 km on the mean Earth radius the distance uses.
private fun point(walkId: String, at: Instant, km: Double) = TrackPoint(walkId, at, 41.39 + km / 111.195, 2.17, 5f)

class ObserveWalkStatsTest {

    private val walkRepository = FakeWalkRepository()
    private val encounterRepository = FakeEncounterRepository()

    @Test
    fun `cats per km reflects the walk's route, and a new point widens it`() = runTest {
        val walk = Walk("w", START, START + 1.hours, "device", START, START + 1.hours)
        walkRepository.upsert(walk)
        walkRepository.appendPoints(
            listOf(point(walk.id, START, km = 0.0), point(walk.id, START + 1.minutes, km = 1.0)),
        )
        encounterRepository.insert(encounterFixture("a", START + 30.minutes))

        ObserveWalkStats(encounterRepository, ObserveWalkTracks(walkRepository))().test {
            val firstMeters = trackLengthMeters(walkRepository.points())
            assertEquals(WalkStats(walkedMeters = firstMeters, catsPerKm = 1 / (firstMeters / 1000)), awaitItem())

            walkRepository.appendPoints(listOf(point(walk.id, START + 2.minutes, km = 2.0)))

            val secondMeters = trackLengthMeters(walkRepository.points())
            assertEquals(WalkStats(walkedMeters = secondMeters, catsPerKm = 1 / (secondMeters / 1000)), awaitItem())
            assertTrue(secondMeters > firstMeters)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
