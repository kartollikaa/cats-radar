package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow

class ObserveEncounterCount(private val encounterRepository: EncounterRepository) {
    operator fun invoke(): Flow<Int> = encounterRepository.observeActiveCount()
}
