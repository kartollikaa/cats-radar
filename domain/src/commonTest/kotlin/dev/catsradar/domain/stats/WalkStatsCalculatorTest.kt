package dev.catsradar.domain.stats

import dev.catsradar.domain.geo.trackLengthMeters
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.model.WalkTrack
import dev.catsradar.domain.testing.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WalkStatsCalculatorTest {

    private fun walk(id: String, start: Instant, end: Instant? = start + 1.hours) =
        Walk(id, start, end, "device", start, end ?: start)

    // One degree of latitude is about 111.195 km on the mean Earth radius the distance uses.
    private fun track(walk: Walk, km: Double) = WalkTrack(
        walk,
        listOf(
            TrackPoint(walk.id, walk.startedAt, 41.39, 2.17, 5f),
            TrackPoint(walk.id, walk.startedAt + 1.minutes, 41.39 + km / 111.195, 2.17, 5f),
        ),
    )

    private fun cat(id: String, at: Instant) = encounterFixture(id, at)

    private fun calculate(cats: List<Encounter>, vararg walks: WalkTrack) =
        WalkStatsCalculator.calculate(cats, walks.toList())

    @Test
    fun `with no walk, nothing is walked and cats per km is unmeasured`() {
        assertEquals(WalkStats(walkedMeters = 0.0, catsPerKm = null), calculate(listOf(cat("a", NOON))))
    }

    @Test
    fun `distance walked adds up every walk's route, a short one and one still on included`() {
        val long = track(walk("long", NOON - 3.hours), km = 2.0)
        val short = track(walk("short", NOON - 1.hours), km = 0.1)
        val open = track(walk("open", NOON, end = null), km = 1.0)

        val stats = calculate(emptyList(), long, short, open)

        val expected = listOf(long, short, open).sumOf { trackLengthMeters(it.points) }
        assertEquals(expected, stats.walkedMeters, absoluteTolerance = 1e-6)
    }

    @Test
    fun `cats per km pools cats and distance across walks rather than averaging them`() {
        val first = track(walk("first", NOON - 3.hours), km = 1.0)
        val second = track(walk("second", NOON - 1.hours), km = 3.0)
        val cats = listOf(
            cat("a", NOON - 170.minutes),
            cat("b", NOON - 160.minutes),
            cat("c", NOON - 150.minutes),
            cat("d", NOON - 30.minutes),
        )

        val stats = calculate(cats, first, second)

        val km = (trackLengthMeters(first.points) + trackLengthMeters(second.points)) / 1000
        assertEquals(4 / km, assertNotNull(stats.catsPerKm), absoluteTolerance = 1e-9)
    }

    @Test
    fun `a walk shorter than the minimum adds distance but is left out of cats per km`() {
        val short = track(walk("short", NOON - 1.hours), km = 0.3)

        val stats = calculate(listOf(cat("a", NOON - 30.minutes)), short)

        assertTrue(stats.walkedMeters > 0.0)
        assertNull(stats.catsPerKm)
    }

    @Test
    fun `a walk with no cats lowers cats per km`() {
        val busy = track(walk("busy", NOON - 3.hours), km = 1.0)
        val quiet = track(walk("quiet", NOON - 1.hours), km = 1.0)
        val cats = listOf(cat("a", NOON - 170.minutes), cat("b", NOON - 160.minutes))

        val stats = calculate(cats, busy, quiet)

        val km = (trackLengthMeters(busy.points) + trackLengthMeters(quiet.points)) / 1000
        assertEquals(2 / km, assertNotNull(stats.catsPerKm), absoluteTolerance = 1e-9)
    }

    @Test
    fun `only live cats inside a walk count, its first and last moments included`() {
        val route = track(walk("w", NOON - 1.hours, end = NOON), km = 1.0)
        val cats = listOf(
            cat("at start", NOON - 1.hours),
            cat("at end", NOON),
            cat("before", NOON - 61.minutes),
            cat("after", NOON + 1.minutes),
            cat("deleted", NOON - 30.minutes).copy(deletedAt = NOON),
        )

        val stats = calculate(cats, route)

        val km = trackLengthMeters(route.points) / 1000
        assertEquals(2 / km, assertNotNull(stats.catsPerKm), absoluteTolerance = 1e-9)
    }

    @Test
    fun `a walk still on counts every cat since it started`() {
        val open = track(walk("open", NOON - 1.hours, end = null), km = 1.0)

        val stats = calculate(listOf(cat("a", NOON - 30.minutes), cat("b", NOON + 6.hours)), open)

        val km = trackLengthMeters(open.points) / 1000
        assertEquals(2 / km, assertNotNull(stats.catsPerKm), absoluteTolerance = 1e-9)
    }

    private companion object {
        val NOON = Instant.parse("2026-09-22T12:00:00Z")
    }
}
