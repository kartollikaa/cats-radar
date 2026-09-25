package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.repository.EncounterRepository

class UndoDelete(private val encounterRepository: EncounterRepository, private val analytics: Analytics) {
    suspend operator fun invoke(id: String) {
        encounterRepository.undoDelete(id)
        analytics.log(AnalyticsEvent.DeleteUndone(1))
    }
}
