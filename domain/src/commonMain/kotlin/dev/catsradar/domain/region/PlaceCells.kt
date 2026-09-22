package dev.catsradar.domain.region

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.repository.PlaceCellRepository

/**
 * Coordinates are only half of a place: the cell they fall in has to exist before anything can name
 * it. Every path that gives an encounter a geohash goes through here, whatever produced the
 * coordinates — a fix, or the photo's own metadata.
 */
object PlaceCells {

    /** The cell [geohash] belongs to, created PENDING if new; resolving it is the geocoder's job. */
    suspend fun remember(repository: PlaceCellRepository, geohash: String): String {
        val cellId = Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION)
        if (repository.loadById(cellId) != null) return cellId

        val bounds = Geohash.decode(cellId)
        repository.upsert(
            PlaceCell(
                cellId = cellId,
                centerLat = (bounds.south + bounds.north) / 2,
                centerLon = (bounds.west + bounds.east) / 2,
                countryCode = null,
                countryName = null,
                adminArea = null,
                locality = null,
                subLocality = null,
                status = PlaceStatus.PENDING,
                attempts = 0,
                lastAttemptAt = null,
                resolvedAt = null,
            ),
        )
        return cellId
    }
}
