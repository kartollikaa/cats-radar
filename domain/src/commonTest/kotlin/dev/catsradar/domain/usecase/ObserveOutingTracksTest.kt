package dev.catsradar.domain.usecase

import app.cash.turbine.test
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.model.WalkTrack
import dev.catsradar.domain.testing.FakeWalkRepository
import dev.catsradar.domain.testing.encounterFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ObserveOutingTracksTest {

    private val walkRepository = FakeWalkRepository()
    private val observeOutingTracks = ObserveOutingTracks(walkRepository)

    private fun walk(id: String, start: Instant, end: Instant?) = Walk(id, start, end, "device", start, end ?: start)

    private suspend fun store(walk: Walk, vararg minutes: Int): WalkTrack {
        val points = minutes.map { TrackPoint(walk.id, walk.startedAt + it.minutes, 41.39 + it / 1000.0, 2.17, 5f) }
        walkRepository.upsert(walk)
        walkRepository.appendPoints(points)
        return WalkTrack(walk, points)
    }

    private fun cats(vararg minutes: Int): MutableStateFlow<List<Encounter>> =
        MutableStateFlow(minutes.map { encounterFixture("cat-$it", BASE + it.minutes) })

    @Test
    fun `an outing gets the tracks of the walks overlapping it and not of a walk outside it`() = runTest {
        val during = store(walk("during", BASE, BASE + 1.hours), 0, 5)
        store(walk("earlier", BASE - 5.hours, BASE - 4.hours), 0, 5)

        observeOutingTracks(cats(10, 20), "cat-20").test {
            assertEquals(listOf(during), awaitItem())
        }
    }

    @Test
    fun `a cat that stretches the outing into another walk's span brings that walk's track in`() = runTest {
        store(walk("morning", BASE, BASE + 30.minutes), 0, 5)
        store(walk("noon", BASE + 50.minutes, BASE + 2.hours), 0, 5)
        val encounters = cats(10)

        observeOutingTracks(encounters, "cat-10").test {
            assertEquals(setOf("morning"), awaitItem().map { it.walk.id }.toSet())

            encounters.update { it + encounterFixture("cat-35", BASE + 35.minutes) }
            encounters.update { it + encounterFixture("cat-55", BASE + 55.minutes) }

            assertEquals(setOf("morning", "noon"), awaitItem().map { it.walk.id }.toSet())
        }
    }

    @Test
    fun `an outing no walk overlaps gets no track`() = runTest {
        store(walk("earlier", BASE - 5.hours, BASE - 4.hours), 0, 5)

        observeOutingTracks(cats(10), "cat-10").test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `a cat that is not there has no outing and so no track`() = runTest {
        store(walk("during", BASE, BASE + 1.hours), 0, 5)

        observeOutingTracks(cats(10), "gone").test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun `a point recorded on an overlapping walk still on brings its longer track`() = runTest {
        store(walk("on", BASE, end = null), 0, 5)

        observeOutingTracks(cats(10), "cat-10").test {
            assertEquals(2, awaitItem().single().points.size)

            walkRepository.appendPoint(TrackPoint("on", BASE + 12.minutes, 41.40, 2.17, 5f))

            assertEquals(3, awaitItem().single().points.size)
        }
    }

    private companion object {
        val BASE = Instant.parse("2026-09-24T10:00:00Z")
    }
}
