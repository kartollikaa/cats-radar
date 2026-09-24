package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.repository.WalkRepository
import kotlin.time.Clock

/** Starts a walk, or returns the one already on: starting twice is one walk. */
class StartWalk(
    private val walkRepository: WalkRepository,
    private val idGenerator: IdGenerator,
    private val deviceIdProvider: DeviceIdProvider,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(): Walk {
        val now = clock.now()
        val fresh = Walk(
            id = idGenerator.newId(),
            startedAt = now,
            endedAt = null,
            deviceId = deviceIdProvider.deviceId,
            createdAt = now,
            updatedAt = now,
        )
        val walk = walkRepository.startIfNoneOpen(fresh)
        if (walk.id == fresh.id) analytics.log(AnalyticsEvent.WalkStarted)
        return walk
    }
}
