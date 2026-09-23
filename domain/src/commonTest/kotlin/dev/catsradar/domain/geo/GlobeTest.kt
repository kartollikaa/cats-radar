package dev.catsradar.domain.geo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobeTest {
    @Test
    fun `the poles and the antimeridian are on the globe`() {
        assertTrue(isOnGlobe(90.0, 180.0))
        assertTrue(isOnGlobe(-90.0, -180.0))
        assertTrue(isOnGlobe(0.0, 0.0))
    }

    @Test
    fun `a latitude past a pole is off the globe`() {
        assertFalse(isOnGlobe(90.000001, 0.0))
        assertFalse(isOnGlobe(-91.0, 0.0))
    }

    @Test
    fun `a longitude past the antimeridian is off the globe`() {
        assertFalse(isOnGlobe(0.0, 180.000001))
        assertFalse(isOnGlobe(0.0, -540.0))
    }

    @Test
    fun `a coordinate that is not a number is off the globe`() {
        assertFalse(isOnGlobe(Double.NaN, 0.0))
        assertFalse(isOnGlobe(0.0, Double.POSITIVE_INFINITY))
    }
}
