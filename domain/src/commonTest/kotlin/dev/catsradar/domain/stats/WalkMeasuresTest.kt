package dev.catsradar.domain.stats

import dev.catsradar.domain.geo.trackLengthMeters
import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.model.WalkTrack
import dev.catsradar.domain.testing.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// A length and a count no real route or cat list here produces, so keeping one proves it was not redone.
private const val KEPT_METERS = 999.0
private const val KEPT_CATS = 42

class WalkMeasuresTest {

    private fun walk(end: Instant? = START + 1.hours) = Walk("w", START, end, "device", START, end ?: START)

    // One degree of latitude is about 111.195 km on the mean Earth radius the distance uses.
    private fun track(walk: Walk, vararg km: Double) = WalkTrack(
        walk,
        km.mapIndexed { index, distance ->
            TrackPoint(walk.id, START + index.minutes, 41.39 + distance / 111.195, 2.17, 5f)
        },
    )

    private fun cats(vararg minutes: Int) =
        CatTimes.of(minutes.map { encounterFixture("cat-$it", START + it.minutes) })

    private fun kept(walk: Walk, pointCount: Int) =
        MeasuredWalk(walk, pointCount, meters = KEPT_METERS, cats = KEPT_CATS)

    @Test
    fun `a first measure gives every walk its route's length and the cats logged during it`() {
        val first = track(walk(), 0.0, 1.0)

        val measures = WalkMeasures.NONE.next(cats(10, 20, 90), listOf(first))

        assertEquals(listOf(MeasuredWalk(first.walk, 2, trackLengthMeters(first.points), cats = 2)), measures.walks)
    }

    @Test
    fun `a route with as many points as before keeps the length measured then`() {
        val route = track(walk(), 0.0, 1.0)
        val previous = WalkMeasures(cats(10), listOf(kept(route.walk, pointCount = 2)))

        val measures = previous.next(cats(10), listOf(route))

        assertEquals(KEPT_METERS, measures.walks.single().meters)
    }

    @Test
    fun `an ended walk whose route gained points, as a backup import can give it, is measured again`() {
        val grown = track(walk(), 0.0, 1.0, 2.0)
        val previous = WalkMeasures(cats(10), listOf(kept(grown.walk, pointCount = 2)))

        val measures = previous.next(cats(10), listOf(grown))

        assertEquals(trackLengthMeters(grown.points), measures.walks.single().meters)
        assertEquals(3, measures.walks.single().pointCount)
    }

    @Test
    fun `a walk over the same span keeps its cat count among equal cats`() {
        val route = track(walk(), 0.0, 1.0)
        val previous = WalkMeasures(cats(10), listOf(kept(route.walk, pointCount = 2)))

        val measures = previous.next(cats(10), listOf(route))

        assertEquals(KEPT_CATS, measures.walks.single().cats)
    }

    @Test
    fun `a walk has its cats counted again once the cats change`() {
        val route = track(walk(), 0.0, 1.0)
        val previous = WalkMeasures(cats(10), listOf(kept(route.walk, pointCount = 2)))

        val measures = previous.next(cats(10, 20), listOf(route))

        assertEquals(2, measures.walks.single().cats)
    }

    @Test
    fun `a walk that ends between two measures has its cats counted again over its new span`() {
        val cats = cats(10, 90)
        val onGoing = WalkMeasures.NONE.next(cats, listOf(track(walk(end = null), 0.0, 1.0)))
        assertEquals(2, onGoing.walks.single().cats)

        val ended = onGoing.next(cats, listOf(track(walk(end = START + 1.hours), 0.0, 1.0)))

        assertEquals(1, ended.walks.single().cats)
    }

    @Test
    fun `walk stats from measured walks pool their kept lengths and counts`() {
        val long = MeasuredWalk(walk(), pointCount = 2, meters = 1500.0, cats = 3)
        val short = MeasuredWalk(walk().copy(id = "short"), pointCount = 2, meters = 100.0, cats = 5)

        val stats = WalkStatsCalculator.calculate(listOf(long, short), minRateDistanceMeters = 500.0)

        assertEquals(WalkStats(walkedMeters = 1600.0, catsPerKm = 2.0), stats)
    }

    private companion object {
        val START = Instant.parse("2026-09-24T10:00:00Z")
    }
}
