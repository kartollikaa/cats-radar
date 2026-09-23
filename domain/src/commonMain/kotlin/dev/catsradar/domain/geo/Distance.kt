package dev.catsradar.domain.geo

import dev.catsradar.domain.model.TrackPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// The mean radius; the few-metre error against the ellipsoid is far below a phone's GPS error.
private const val EARTH_RADIUS_METERS = 6_371_008.8

/** Great-circle distance between two points, in metres. */
fun distanceMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
    val phi1 = fromLat.radians()
    val phi2 = toLat.radians()
    val dPhi = phi2 - phi1
    val dLambda = (toLon - fromLon).radians()
    val h = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

private const val DEGREES_PER_HALF_TURN = 180.0

private fun Double.radians(): Double = this * PI / DEGREES_PER_HALF_TURN

/** The length of a route walked through [points] in their order, in metres. */
fun trackLengthMeters(points: List<TrackPoint>): Double =
    points.zipWithNext().sumOf { (a, b) -> distanceMeters(a.lat, a.lon, b.lat, b.lon) }
