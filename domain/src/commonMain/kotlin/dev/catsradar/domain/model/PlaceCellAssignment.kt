package dev.catsradar.domain.model

/** The geohash and place cell one cat's coordinates imply, for the row still at [lat], [lon]. */
data class PlaceCellAssignment(
    val encounterId: String,
    val lat: Double,
    val lon: Double,
    val geohash: String,
    val placeCellId: String,
)
