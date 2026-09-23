package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import kotlin.time.Clock

class UndoImport(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(ids: List<String>) {
        encounterRepository.softDeleteAll(ids, clock.now())
    }
}
