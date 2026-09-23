package dev.catsradar.domain.backup

import dev.catsradar.domain.geo.isOnGlobe
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource

/** A location that is not a point on the globe costs the cat its location, not the archive its import. */
internal fun Encounter.withoutLocationUnlessOnGlobe(): Encounter {
    if (lat != null && lon != null && isOnGlobe(lat, lon)) return this
    return copy(
        lat = null,
        lon = null,
        accuracyMeters = null,
        locationSource = LocationSource.NONE,
        locationFixedAt = null,
        geohash = null,
        placeCellId = null,
    )
}
