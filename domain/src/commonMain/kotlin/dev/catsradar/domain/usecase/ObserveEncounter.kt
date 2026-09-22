package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow

class ObserveEncounter(private val encounterRepository: EncounterRepository) {
    /** Emits null when no live encounter has this id: unknown, purged, or soft-deleted. */
    operator fun invoke(id: String): Flow<Encounter?> = encounterRepository.observeById(id)
}
