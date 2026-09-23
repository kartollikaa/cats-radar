package dev.catsradar.domain.backup

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.region.PlaceCells

/**
 * An archive's cell is trusted for its name alone — not for its centre, which the id decides, nor for
 * another device's failed lookups. Null when the id is not a place cell this app could have written.
 */
internal fun PlaceCell.asImported(): PlaceCell? = when {
    !Geohash.isWellFormed(cellId, Tuning.PLACE_CELL_PRECISION) -> null
    status != PlaceStatus.RESOLVED -> PlaceCells.untried(cellId)
    else -> Geohash.decode(cellId).let { bounds -> copy(centerLat = bounds.centerLat, centerLon = bounds.centerLon) }
}
