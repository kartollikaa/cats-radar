package dev.catsradar.domain.geo

internal const val MIN_LATITUDE = -90.0
internal const val MAX_LATITUDE = 90.0
internal const val MIN_LONGITUDE = -180.0
internal const val MAX_LONGITUDE = 180.0

data class GeoPoint(val lat: Double, val lon: Double)

internal fun isOnGlobe(lat: Double, lon: Double): Boolean =
    lat in MIN_LATITUDE..MAX_LATITUDE && lon in MIN_LONGITUDE..MAX_LONGITUDE

internal fun pointOnGlobe(lat: Double?, lon: Double?): GeoPoint? =
    if (lat != null && lon != null && isOnGlobe(lat, lon)) GeoPoint(lat, lon) else null
