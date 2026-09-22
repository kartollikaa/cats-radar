package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow

class ObserveEncounters(private val encounterRepository: EncounterRepository) {
    operator fun invoke(): Flow<List<Encounter>> = encounterRepository.observeAll()
}
