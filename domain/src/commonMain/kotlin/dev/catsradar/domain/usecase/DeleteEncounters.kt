package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.DeletedBatch
import dev.catsradar.domain.repository.EncounterRepository
import kotlin.time.Clock

class DeleteEncounters(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(ids: Collection<String>): DeletedBatch {
        val batch = DeletedBatch(ids = ids.toList(), deletedAt = clock.now())
        encounterRepository.softDeleteAll(batch.ids, batch.deletedAt)
        return batch
    }
}
