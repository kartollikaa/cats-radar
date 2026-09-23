package dev.catsradar.domain.geo

internal const val MIN_LATITUDE = -90.0
internal const val MAX_LATITUDE = 90.0
internal const val MIN_LONGITUDE = -180.0
internal const val MAX_LONGITUDE = 180.0

internal fun isOnGlobe(lat: Double, lon: Double): Boolean =
    lat in MIN_LATITUDE..MAX_LATITUDE && lon in MIN_LONGITUDE..MAX_LONGITUDE
