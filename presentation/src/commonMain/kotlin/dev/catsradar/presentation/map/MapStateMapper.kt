package dev.catsradar.presentation.map

import dev.catsradar.domain.model.Encounter
import dev.catsradar.presentation.coat.toOption
import kotlinx.collections.immutable.toImmutableList

// About a kilometre: one cat, or several on one street, should open on the street rather than a doorstep.
private const val MIN_AREA_DEGREES = 0.01

class MapStateMapper {

    fun map(encounters: List<Encounter>): MapState {
        val points = encounters.mapNotNull { it.toPoint() }
        if (points.isEmpty()) return MapState.Empty
        return MapState.Located(points = points.toImmutableList(), area = areaAround(points))
    }

    private fun Encounter.toPoint(): MapPoint? {
        val latitude = lat
        val longitude = lon
        if (deletedAt != null || latitude == null || longitude == null) return null
        return MapPoint(id = id, latitude = latitude, longitude = longitude, coat = coat?.toOption())
    }

    private fun areaAround(points: List<MapPoint>): MapArea {
        val south = points.minOf { it.latitude }
        val north = points.maxOf { it.latitude }
        val west = points.minOf { it.longitude }
        val east = points.maxOf { it.longitude }
        val latitudePad = ((MIN_AREA_DEGREES - (north - south)) / 2).coerceAtLeast(0.0)
        val longitudePad = ((MIN_AREA_DEGREES - (east - west)) / 2).coerceAtLeast(0.0)
        return MapArea(
            south = south - latitudePad,
            west = west - longitudePad,
            north = north + latitudePad,
            east = east + longitudePad,
        )
    }
}
