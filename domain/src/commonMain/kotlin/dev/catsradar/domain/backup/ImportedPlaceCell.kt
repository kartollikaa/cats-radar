package dev.catsradar.domain.backup

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.region.PlaceCells

/**
 * An archive's cell is trusted for what it is called, never for where it is: the id is the place, and
 * the centre is derived from it. Null when the id is not a place cell this app could have written.
 */
internal fun PlaceCell.withCenterFromId(): PlaceCell? {
    if (!Geohash.isWellFormed(cellId, Tuning.PLACE_CELL_PRECISION)) return null
    val bounds = Geohash.decode(cellId)
    return copy(centerLat = bounds.centerLat, centerLon = bounds.centerLon)
}

/**
 * Another device's lookups say nothing about this device's geocoder, so a cell that arrives without a
 * name arrives as if this device had just come across it.
 */
internal fun PlaceCell.withLookupsFromHere(): PlaceCell =
    if (status == PlaceStatus.RESOLVED) this else PlaceCells.untried(cellId)
