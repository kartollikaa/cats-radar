package dev.catsradar.domain.geo

import dev.catsradar.domain.model.TrackPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class DistanceTest {

    private fun point(lat: Double, lon: Double, second: Int) =
        TrackPoint("walk", Instant.fromEpochSeconds(second.toLong()), lat, lon, 5f)

    @Test
    fun `a degree of latitude is about a hundred and eleven kilometres`() {
        val meters = distanceMeters(41.0, 2.0, 42.0, 2.0)

        assertTrue(abs(meters - 111_195.0) < 50.0, "$meters")
    }

    @Test
    fun `Barcelona to Madrid is about five hundred and five kilometres as the crow flies`() {
        val meters = distanceMeters(41.3874, 2.1686, 40.4168, -3.7038)

        assertTrue(abs(meters - 505_000.0) < 2_000.0, "$meters")
    }

    @Test
    fun `the same point is no distance at all, in either order`() {
        assertEquals(0.0, distanceMeters(41.39, 2.17, 41.39, 2.17))
        assertEquals(distanceMeters(41.39, 2.17, 41.40, 2.18), distanceMeters(41.40, 2.18, 41.39, 2.17))
    }

    @Test
    fun `a route is as long as its legs together, and a route of one point has no length`() {
        val a = point(41.0, 2.0, 0)
        val b = point(42.0, 2.0, 1)
        val c = point(42.0, 3.0, 2)

        val total = trackLengthMeters(listOf(a, b, c))

        assertEquals(distanceMeters(41.0, 2.0, 42.0, 2.0) + distanceMeters(42.0, 2.0, 42.0, 3.0), total)
        assertEquals(0.0, trackLengthMeters(listOf(a)))
        assertEquals(0.0, trackLengthMeters(emptyList()))
    }
}
