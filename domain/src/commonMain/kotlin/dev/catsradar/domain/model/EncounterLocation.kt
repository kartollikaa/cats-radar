package dev.catsradar.domain.model

import dev.catsradar.domain.geo.GeoPoint
import dev.catsradar.domain.geo.pointOnGlobe

/** Where the cat was, or null when it has no location: no point on the globe, or marked [LocationSource.NONE]. */
internal fun Encounter.locatedPoint(): GeoPoint? =
    pointOnGlobe(lat, lon)?.takeIf { locationSource != LocationSource.NONE }
