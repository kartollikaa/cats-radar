package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import kotlin.time.Clock

class UndoImport(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(ids: List<String>) {
        // One instant for the whole run: the cats went in together and they come out together,
        // so the purge worker retires them on the same day rather than trickling.
        val deletedAt = clock.now()
        ids.forEach { encounterRepository.softDelete(it, deletedAt) }
    }
}
