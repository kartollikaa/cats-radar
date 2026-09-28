package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveEncounterNumber(private val encounterRepository: EncounterRepository) {
    /** The cat's place among the live cats, oldest first, again whenever it moves; null while it is not live. */
    operator fun invoke(id: String): Flow<Int?> = encounterRepository.observeNumber(id).distinctUntilChanged()
}
