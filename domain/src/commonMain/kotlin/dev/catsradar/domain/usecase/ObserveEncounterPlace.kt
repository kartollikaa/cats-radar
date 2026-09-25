package dev.catsradar.domain.usecase

import dev.catsradar.domain.region.EncounterPlace
import dev.catsradar.domain.region.placeIn
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveEncounterPlace(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
) {
    /** Where cat [encounterId] was seen, again whenever its cell is named or renamed; null while it has none. */
    operator fun invoke(encounterId: String): Flow<EncounterPlace?> =
        combine(encounterRepository.observeById(encounterId), placeCellRepository.observeAll()) { encounter, cells ->
            encounter?.placeIn(cells)
        }.distinctUntilChanged()
}
