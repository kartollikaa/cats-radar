package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.DeletedBatch
import dev.catsradar.domain.repository.EncounterRepository

class UndoDeleteEncounters(private val encounterRepository: EncounterRepository) {
    suspend operator fun invoke(batch: DeletedBatch) {
        encounterRepository.undoDeleteAll(batch.ids, batch.deletedAt)
    }
}
