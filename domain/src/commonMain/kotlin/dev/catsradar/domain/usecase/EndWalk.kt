package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import kotlin.time.Clock

/**
 * Ends the walk that is on, never before it started; returns it as ended, or null when none was on
 * or another call ended it first.
 */
class EndWalk(
    private val walkRepository: WalkRepository,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(): Walk? {
        val open = walkRepository.openWalk() ?: return null
        val now = clock.now()
        val endedAt = maxOf(now, open.startedAt)
        val ended = walkRepository.end(open.id, endedAt, updatedAt = now)
        if (ended) analytics.log(AnalyticsEvent.WalkEnded((endedAt - open.startedAt).inWholeMinutes))
        return if (ended) open.copy(endedAt = endedAt, updatedAt = now) else null
    }
}
