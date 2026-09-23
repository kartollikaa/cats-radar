package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import kotlin.time.Clock

/** Ends the walk that is on, if one is; returns it as ended, or null when none was on. */
class EndWalk(private val walkRepository: WalkRepository, private val clock: Clock) {
    suspend operator fun invoke(): Walk? {
        val open = walkRepository.openWalk() ?: return null
        val now = clock.now()
        walkRepository.end(open.id, now)
        return open.copy(endedAt = now, updatedAt = now)
    }
}
