package dev.catsradar.domain.photo

import dev.catsradar.domain.geo.isOnGlobe
import dev.catsradar.domain.platform.ExifData

internal val ExifData.hasLocationOnGlobe: Boolean
    get() = lat != null && lon != null && isOnGlobe(lat, lon)
