package dev.catsradar.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MetersPerDpTest {

    @Test
    fun `at the equator and street zoom a dp is mbgl's 2_39 meters`() {
        assertEquals(2.388657, metersPerDp(zoom = 15.0, latitude = 0.0), 1e-6)
    }

    @Test
    fun `a dp covers half as much ground at sixty degrees, and half again a zoom level in`() {
        assertEquals(2.388657 / 2, metersPerDp(zoom = 15.0, latitude = 60.0), 1e-6)
        assertEquals(2.388657 / 4, metersPerDp(zoom = 16.0, latitude = 60.0), 1e-6)
    }

    @Test
    fun `past web mercator's last latitude the scale stops at its edge`() {
        val edge = metersPerDp(zoom = 15.0, latitude = 85.051128779806604)
        assertEquals(edge, metersPerDp(zoom = 15.0, latitude = 90.0), 0.0)
    }
}
