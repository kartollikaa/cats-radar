package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The walk that has not ended, or null, again each time a walk starts or ends. */
class ObserveOpenWalk(private val walkRepository: WalkRepository) {
    operator fun invoke(): Flow<Walk?> =
        walkRepository.observeAll()
            .map { walks -> walks.firstOrNull { it.endedAt == null } }
            .distinctUntilChanged()
}
