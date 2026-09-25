package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.WalkTrack
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Every walk, newest start first, each with its route; again whenever a walk or a point changes. */
class ObserveWalkTracks(private val walkRepository: WalkRepository) {
    operator fun invoke(): Flow<List<WalkTrack>> =
        combine(walkRepository.observeAll(), walkRepository.observeEveryPoint()) { walks, points ->
            val routes = points.groupBy { it.walkId }
            walks.map { walk -> WalkTrack(walk, routes[walk.id].orEmpty()) }
        }
}
