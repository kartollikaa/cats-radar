package dev.catsradar.domain.geo

data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    // Half-open [south, north) x [west, east): adjoining cells share an edge, and only one of them
    // should claim a point on it. Closed at +90/+180, the true edge of the coordinate system itself.
    fun contains(lat: Double, lon: Double): Boolean {
        val latInside = lat >= south && (lat < north || north == MAX_LATITUDE)
        val lonInside = lon >= west && (lon < east || east == MAX_LONGITUDE)
        return latInside && lonInside
    }
}
