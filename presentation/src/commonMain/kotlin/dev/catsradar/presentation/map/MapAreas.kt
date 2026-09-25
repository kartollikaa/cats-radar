package dev.catsradar.presentation.map

// About a kilometre: one cat, or several on one street, should open on the street rather than a doorstep.
private const val MIN_AREA_DEGREES = 0.01

private const val MAX_LATITUDE = 90.0

/** The area around [points], never smaller than a street. */
internal fun areaAround(points: List<MapPosition>): MapArea {
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
