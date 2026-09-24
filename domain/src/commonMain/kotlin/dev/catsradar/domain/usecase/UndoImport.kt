package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.repository.EncounterRepository
import kotlin.time.Clock

class UndoImport(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(ids: List<String>) {
        encounterRepository.softDeleteAll(ids, clock.now())
        analytics.log(AnalyticsEvent.ImportUndone(ids.size))
    }
}
