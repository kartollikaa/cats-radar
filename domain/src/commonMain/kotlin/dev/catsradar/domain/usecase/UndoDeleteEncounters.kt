package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.model.DeletedBatch
import dev.catsradar.domain.repository.EncounterRepository

class UndoDeleteEncounters(
    private val encounterRepository: EncounterRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(batch: DeletedBatch) {
        encounterRepository.undoDeleteAll(batch.ids, batch.deletedAt)
        analytics.log(AnalyticsEvent.DeleteUndone(batch.ids.size))
    }
}
