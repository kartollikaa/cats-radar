package dev.catsradar.domain.backup

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.GeoPoint
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.geo.pointOnGlobe
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource

/**
 * An archive's coordinates are trusted only as a point on the globe, and nothing derived from them
 * is trusted at all. A bad location costs the cat its location, not the archive its import.
 */
internal fun Encounter.withLocationFromCoordinates(): Encounter =
    pointOnGlobe(lat, lon)
        ?.takeIf { locationSource != LocationSource.NONE }
        ?.let { withCellsOf(it) }
        ?: withoutLocation()

private fun Encounter.withCellsOf(point: GeoPoint): Encounter {
    val geohash = Geohash.encode(point.lat, point.lon, Tuning.GEOHASH_PRECISION)
    return copy(geohash = geohash, placeCellId = Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION))
}

private fun Encounter.withoutLocation(): Encounter = copy(
    lat = null,
    lon = null,
    accuracyMeters = null,
    locationSource = LocationSource.NONE,
    locationFixedAt = null,
    geohash = null,
    placeCellId = null,
)
