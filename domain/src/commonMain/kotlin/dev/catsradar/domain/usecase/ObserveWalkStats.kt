package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.stats.WalkStats
import dev.catsradar.domain.stats.WalkStatsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveWalkStats(
    private val encounterRepository: EncounterRepository,
    private val observeWalkTracks: ObserveWalkTracks,
) {
    operator fun invoke(): Flow<WalkStats> =
        combine(encounterRepository.observeAll(), observeWalkTracks()) { encounters, walks ->
            WalkStatsCalculator.calculate(encounters, walks)
        }
}
