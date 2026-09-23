package dev.catsradar.domain.backup

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.PlaceCell

/**
 * An archive's cell is trusted for what it is called, never for where it is: the id is the place, and
 * the centre is derived from it. Null when the id is not a place cell this app could have written.
 */
internal fun PlaceCell.withCenterFromId(): PlaceCell? {
    if (!Geohash.isWellFormed(cellId, Tuning.PLACE_CELL_PRECISION)) return null
    val bounds = Geohash.decode(cellId)
    return copy(centerLat = bounds.centerLat, centerLon = bounds.centerLon)
}
