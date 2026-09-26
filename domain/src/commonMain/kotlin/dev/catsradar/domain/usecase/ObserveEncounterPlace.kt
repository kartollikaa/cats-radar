package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.region.EncounterPlace
import dev.catsradar.domain.region.placeIn
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveEncounterPlace(private val placeCellRepository: PlaceCellRepository) {
    /** Where [encounter] was found, again whenever its cell is named or renamed; null while it has no named place. */
    operator fun invoke(encounter: Encounter?): Flow<EncounterPlace?> {
        val cellId = encounter?.placeCellId ?: return flowOf(null)
        return placeCellRepository.observeById(cellId).map { encounter.placeIn(it) }.distinctUntilChanged()
    }
}
