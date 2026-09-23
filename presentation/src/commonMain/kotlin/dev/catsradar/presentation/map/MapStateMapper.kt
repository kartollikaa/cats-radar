package dev.catsradar.presentation.map

import dev.catsradar.domain.model.Encounter
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.encounters.EncountersStateMapper
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate

// About a kilometre: one cat, or several on one street, should open on the street rather than a doorstep.
private const val MIN_AREA_DEGREES = 0.01

private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

class MapStateMapper(private val encountersMapper: EncountersStateMapper) {

    /** [spot] holds the ids of the cats the user opened together, if any. */
    fun map(encounters: List<Encounter>, today: LocalDate, spot: Set<String>? = null): MapState {
        val points = encounters.mapNotNull { it.toPoint() }
        if (points.isEmpty()) return MapState.Empty
        return MapState.Located(
            points = points.toImmutableList(),
            area = areaAround(points),
            spot = spot?.let { ids -> spotOf(encounters.filter { it.id in ids && it.deletedAt == null }, today) },
        )
    }

    private fun spotOf(cats: List<Encounter>, today: LocalDate): MapSpot? {
        if (cats.isEmpty()) return null
        return MapSpot(catCount = cats.size, rows = encountersMapper.map(cats, today).rows)
    }

    private fun Encounter.toPoint(): MapPoint? {
        val latitude = lat
        val longitude = lon
        if (deletedAt != null || latitude == null || longitude == null) return null
        val point = MapPoint(id = id, latitude = latitude, longitude = longitude, coat = coat?.toOption())
        return if (isOnEarth(latitude, longitude)) point else null
    }

    private fun isOnEarth(latitude: Double, longitude: Double): Boolean =
        latitude in -MAX_LATITUDE..MAX_LATITUDE && longitude in -MAX_LONGITUDE..MAX_LONGITUDE

    private fun areaAround(points: List<MapPoint>): MapArea {
        val south = points.minOf { it.latitude }
        val north = points.maxOf { it.latitude }
        val west = points.minOf { it.longitude }
        val east = points.maxOf { it.longitude }
        val latitudePad = ((MIN_AREA_DEGREES - (north - south)) / 2).coerceAtLeast(0.0)
        val longitudePad = ((MIN_AREA_DEGREES - (east - west)) / 2).coerceAtLeast(0.0)
        return MapArea(
            south = (south - latitudePad).coerceAtLeast(-MAX_LATITUDE),
            west = west - longitudePad,
            north = (north + latitudePad).coerceAtMost(MAX_LATITUDE),
            east = east + longitudePad,
        )
    }
}
