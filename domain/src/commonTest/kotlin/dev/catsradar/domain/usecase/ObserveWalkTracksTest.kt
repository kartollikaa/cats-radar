package dev.catsradar.domain.usecase

import app.cash.turbine.test
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.testing.FakeWalkRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val START = Instant.parse("2026-09-24T10:00:00Z")

private fun walk(id: String, startedAt: Instant) =
    Walk(id, startedAt, startedAt + 1.hours, "device", startedAt, startedAt)

private fun point(walkId: String, minute: Int) = TrackPoint(walkId, START + minute.minutes, 41.39, 2.17, 5f)

class ObserveWalkTracksTest {

    private val repository = FakeWalkRepository()

    @Test
    fun `each walk carries only its own points, in route order, newest walk first`() = runTest {
        repository.upsert(walk("morning", START))
        repository.upsert(walk("evening", START + 8.hours))
        repository.appendPoints(
            listOf(point("evening", minute = 1), point("morning", minute = 2), point("morning", minute = 1)),
        )

        val tracks = ObserveWalkTracks(repository)().first()

        assertEquals(listOf("evening", "morning"), tracks.map { it.walk.id })
        assertEquals(listOf(point("evening", 1)), tracks[0].points)
        assertEquals(listOf(point("morning", 1), point("morning", 2)), tracks[1].points)
    }

    @Test
    fun `a walk with no recorded point has an empty route`() = runTest {
        repository.upsert(walk("morning", START))

        val tracks = ObserveWalkTracks(repository)().first()

        assertEquals(emptyList(), tracks.single().points)
    }

    @Test
    fun `a new point reaches the walk's route`() = runTest {
        repository.upsert(walk("morning", START))

        ObserveWalkTracks(repository)().test {
            assertEquals(emptyList(), awaitItem().single().points)

            repository.appendPoints(listOf(point("morning", minute = 1)))

            assertEquals(listOf(point("morning", 1)), awaitItem().single().points)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
