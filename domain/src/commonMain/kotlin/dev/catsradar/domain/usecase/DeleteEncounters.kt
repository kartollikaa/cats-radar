package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.model.DeletedBatch
import dev.catsradar.domain.repository.EncounterRepository
import kotlin.time.Clock

class DeleteEncounters(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(ids: Collection<String>): DeletedBatch {
        val batch = DeletedBatch(ids = ids.toList(), deletedAt = clock.now())
        encounterRepository.softDeleteAll(batch.ids, batch.deletedAt)
        analytics.log(AnalyticsEvent.CatsDeleted(batch.ids.size))
        return batch
    }
}
