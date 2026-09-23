package dev.catsradar.domain.usecase

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.PlaceCellAssignment
import dev.catsradar.domain.model.locatedPoint
import dev.catsradar.domain.region.PlaceCells
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.first

/**
 * Gives every cat with a location, deleted ones included, the geohash and place cell its coordinates
 * imply, and creates that cell pending when it does not exist yet.
 */
class RepairPlaceCells(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
) {
    suspend operator fun invoke() {
        val knownCells = placeCellRepository.observeAll().first().mapTo(mutableSetOf()) { it.cellId }
        val assignments = encounterRepository.loadEvery().mapNotNull { encounter ->
            val point = encounter.locatedPoint() ?: return@mapNotNull null
            val geohash = Geohash.encode(point.lat, point.lon, Tuning.GEOHASH_PRECISION)
            val cellId = Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION)
            if (cellId !in knownCells) knownCells += PlaceCells.remember(placeCellRepository, geohash)
            PlaceCellAssignment(encounter.id, point.lat, point.lon, geohash, cellId)
                .takeIf { encounter.geohash != geohash || encounter.placeCellId != cellId }
        }
        if (assignments.isNotEmpty()) encounterRepository.setPlaceCells(assignments)
    }
}
