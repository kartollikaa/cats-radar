package dev.catsradar.domain.geo

data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    fun contains(lat: Double, lon: Double): Boolean = lat in south..north && lon in west..east
}
