package dev.catsradar.presentation.locationpicker

import dev.catsradar.domain.geo.GeoPoint
import dev.catsradar.presentation.map.MapArea
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationPickerStateMapperTest {

    private val mapper = LocationPickerStateMapper()

    @Test
    fun `a place to look opens the map on a street around it`() {
        assertEquals(
            LocationPickerState.Picking(start = MapArea(south = 41.39, west = 2.17, north = 41.40, east = 2.18)),
            mapper.picking(GeoPoint(lat = 41.395, lon = 2.175)).rounded(),
        )
    }

    @Test
    fun `nowhere to look opens the whole world`() {
        assertEquals(LocationPickerState.Picking(start = null), mapper.picking(null))
    }

    @Test
    fun `the phone's position is a street around it`() {
        assertEquals(
            MapArea(south = 55.75, west = 37.61, north = 55.76, east = 37.62),
            mapper.areaAround(GeoPoint(lat = 55.755, lon = 37.615)).rounded(),
        )
    }

    private fun LocationPickerState.Picking.rounded() = copy(start = start?.rounded())

    private fun MapArea.rounded() = MapArea(south.round(), west.round(), north.round(), east.round())

    private fun Double.round() = kotlin.math.round(this * 100) / 100
}
