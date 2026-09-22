package dev.catsradar.domain.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeohashTest {
    @Test
    fun `encodes the published Jutland vector at precision 11`() {
        // geohash.org / Wikipedia's worked example: near Jutland, Denmark.
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
    }

    @Test
    fun `encodes a southern-hemisphere vector with positive longitude`() {
        // Sydney, AU; cross-checked against chrisveness/latlon-geohash.
        assertEquals("r3gx2f77b", Geohash.encode(-33.8688, 151.2093, 9))
    }

    @Test
    fun `encodes a vector with negative latitude and negative longitude`() {
        // Rio de Janeiro, BR; cross-checked against chrisveness/latlon-geohash.
        assertEquals("75cm9tfqn", Geohash.encode(-22.9068, -43.1729, 9))
    }

    @Test
    fun `decoding an encoded point lands within its own cell`() {
        FIXED_COORDINATES.forEach { (lat, lon) ->
            val box = Geohash.decode(Geohash.encode(lat, lon, 9))
            assertTrue(box.contains(lat, lon), "($lat, $lon) not inside its own cell $box")
        }
    }

    @Test
    fun `a shorter encoding equals the longer encoding's prefix`() {
        FIXED_COORDINATES.forEach { (lat, lon) ->
            assertEquals(Geohash.encode(lat, lon, 5), Geohash.encode(lat, lon, 8).take(5))
        }
    }

    @Test
    fun `prefix truncates and refuses to widen`() {
        val hash = Geohash.encode(57.64911, 10.40744, 8)
        assertEquals(hash.take(5), Geohash.prefix(hash, 5))
        assertFailsWith<IllegalArgumentException> { Geohash.prefix(hash, 9) }
    }

    @Test
    fun `rejects precision outside 1 to 12`() {
        assertFailsWith<IllegalArgumentException> { Geohash.encode(0.0, 0.0, 0) }
        assertFailsWith<IllegalArgumentException> { Geohash.encode(0.0, 0.0, 13) }
    }

    @Test
    fun `rejects out-of-range latitude`() {
        assertFailsWith<IllegalArgumentException> { Geohash.encode(90.1, 0.0, 5) }
        assertFailsWith<IllegalArgumentException> { Geohash.encode(-90.1, 0.0, 5) }
    }

    @Test
    fun `rejects out-of-range longitude`() {
        assertFailsWith<IllegalArgumentException> { Geohash.encode(0.0, 180.1, 5) }
        assertFailsWith<IllegalArgumentException> { Geohash.encode(0.0, -180.1, 5) }
    }

    @Test
    fun `rejects an invalid character on decode`() {
        assertFailsWith<IllegalArgumentException> { Geohash.decode("u4pra") }
        assertFailsWith<IllegalArgumentException> { Geohash.decode("") }
    }

    private companion object {
        val FIXED_COORDINATES = listOf(
            0.0 to 0.0,
            57.64911 to 10.40744,
            -33.8688 to 151.2093,
            -22.9068 to -43.1729,
            90.0 to 180.0,
            -90.0 to -180.0,
            51.5074 to -0.1278,
            40.7128 to -74.0060,
            35.6762 to 139.6503,
            -1.2921 to 36.8219,
            55.7558 to 37.6173,
            48.8566 to 2.3522,
            -34.6037 to -58.3816,
            1.3521 to 103.8198,
            64.1466 to -21.9426,
            -41.2865 to 174.7762,
            25.2048 to 55.2708,
            30.0444 to 31.2357,
            -23.5505 to -46.6333,
            52.5200 to 13.4050,
            19.4326 to -99.1332,
        )
    }
}
