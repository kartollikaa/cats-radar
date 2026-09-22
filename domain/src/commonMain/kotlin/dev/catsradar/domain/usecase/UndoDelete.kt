package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository

class UndoDelete(private val encounterRepository: EncounterRepository) {
    suspend operator fun invoke(id: String) {
        encounterRepository.undoDelete(id)
    }
}
