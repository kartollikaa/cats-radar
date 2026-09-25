package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.stats.CatTimes
import dev.catsradar.domain.stats.WalkMeasures
import dev.catsradar.domain.stats.WalkStats
import dev.catsradar.domain.stats.WalkStatsCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

class ObserveWalkStats(
    private val observeWalkTracks: ObserveWalkTracks,
    private val computeDispatcher: CoroutineDispatcher,
) {
    /**
     * Walk stats of every walk's route and the cats among [encounters], again whenever either changes,
     * worked out on [computeDispatcher]; a collector that falls behind receives only the latest.
     */
    operator fun invoke(encounters: Flow<List<Encounter>>): Flow<WalkStats> =
        combine(encounters.map(CatTimes::of), observeWalkTracks()) { cats, tracks -> cats to tracks }
            .scan(WalkMeasures.NONE) { measures, (cats, tracks) -> measures.next(cats, tracks) }
            .drop(1)
            .map { WalkStatsCalculator.calculate(it.walks) }
            .conflate()
            .flowOn(computeDispatcher)
}
