package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import kotlin.time.Clock

class DeleteEncounter(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: String) {
        encounterRepository.softDelete(id, clock.now())
    }
}
