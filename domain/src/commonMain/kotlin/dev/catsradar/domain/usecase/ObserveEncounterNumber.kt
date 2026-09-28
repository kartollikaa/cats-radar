package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveEncounterNumber(private val encounterRepository: EncounterRepository) {
    /** [EncounterRepository.observeNumber], passed on only when it changes. */
    operator fun invoke(id: String): Flow<Int?> = encounterRepository.observeNumber(id).distinctUntilChanged()
}
