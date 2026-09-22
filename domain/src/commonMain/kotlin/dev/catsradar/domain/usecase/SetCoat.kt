package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

class SetCoat(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
) {
    /** [coat] of null clears it; a cat whose colour you misread should be correctable to unknown. */
    suspend operator fun invoke(encounterId: String, coat: CatCoat?) {
        // A soft-deleted encounter is not there to be edited, and writing to it would also
        // resurrect nothing useful: observeById already hides it.
        val target = encounterRepository.observeById(encounterId).first() ?: return
        if (target.coat == coat) return
        encounterRepository.update(target.copy(coat = coat, updatedAt = clock.now()))
    }
}
