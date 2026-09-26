package dev.catsradar.presentation.locationpicker

import dev.catsradar.domain.geo.GeoPoint
import dev.catsradar.presentation.map.MapArea
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.presentation.map.areaAround as areaAroundPositions

class LocationPickerStateMapper {

    fun picking(start: GeoPoint?): LocationPickerState.Picking =
        LocationPickerState.Picking(start = start?.let(::areaAround))

    fun areaAround(point: GeoPoint): MapArea =
        areaAroundPositions(listOf(MapPosition(point.lat, point.lon)))
}
