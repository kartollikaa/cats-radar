package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.repository.EncounterRepository
import kotlin.time.Clock

class DeleteEncounter(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(id: String) {
        encounterRepository.softDelete(id, clock.now())
        analytics.log(AnalyticsEvent.CatsDeleted(1))
    }
}
