package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.geo.pointOnGlobe
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.region.PlaceCells
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.first

/**
 * Gives every located cat the geohash and place cell its coordinates imply, creating a missing cell
 * pending: a cat with coordinates but no cell is never looked up, so it would stay unnamed.
 */
class RepairPlaceCells(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
) {
    suspend operator fun invoke() {
        val knownCells = placeCellRepository.observeAll().first().mapTo(mutableSetOf()) { it.cellId }
        encounterRepository.observeAll().first().forEach { encounter ->
            val point = pointOnGlobe(encounter.lat, encounter.lon)
                ?.takeIf { encounter.locationSource != LocationSource.NONE }
                ?: return@forEach
            val geohash = Geohash.encode(point.lat, point.lon, Tuning.GEOHASH_PRECISION)
            val cellId = Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION)
            if (cellId !in knownCells) knownCells += PlaceCells.remember(placeCellRepository, geohash)
            if (encounter.geohash != geohash || encounter.placeCellId != cellId) {
                encounterRepository.setPlaceCell(encounter.id, point.lat, point.lon, geohash, cellId)
            }
        }
    }
}
