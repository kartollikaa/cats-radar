package dev.catsradar.ui.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

private const val TileSize = 512.0
private const val EarthCircumferenceMeters = 2.0 * PI * 6378137.0
private const val MaxMercatorLatitude = 85.051128779806604

// mbgl::Projection::getMetersPerPixelAtLatitude, as maplibre-compose's Viewport.metersPerDpAtTarget computes it.
internal fun metersPerDp(zoom: Double, latitude: Double): Double {
    val clampedLatitude = latitude.coerceIn(-MaxMercatorLatitude, MaxMercatorLatitude)
    return cos(clampedLatitude * PI / 180.0) * EarthCircumferenceMeters / (2.0.pow(zoom) * TileSize)
}
