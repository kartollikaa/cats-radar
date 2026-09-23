package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import kotlin.time.Clock

/**
 * Ends the walk that is on, never before it started; returns it as ended, or null when none was on
 * or another call ended it first.
 */
class EndWalk(private val walkRepository: WalkRepository, private val clock: Clock) {
    suspend operator fun invoke(): Walk? {
        val open = walkRepository.openWalk() ?: return null
        val endedAt = maxOf(clock.now(), open.startedAt)
        val ended = walkRepository.end(open.id, endedAt)
        return if (ended) open.copy(endedAt = endedAt, updatedAt = endedAt) else null
    }
}
