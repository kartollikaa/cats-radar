package dev.catsradar.presentation.statistics

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.stats.WalkStats
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.ObserveWalkStats
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class StatisticsStore(
    observeStats: ObserveStats,
    observeWalkStats: ObserveWalkStats,
    private val stateMapper: StatisticsStateMapper,
) : Store<StatisticsState, StatisticsIntent, StatisticsEffect>(StatisticsState()) {

    init {
        val walks: Flow<WalkStats?> = flow {
            emit(null)
            emitAll(observeWalkStats())
        }
        combine(observeStats(), walks) { stats, walk ->
            walk?.let { stateMapper.map(stats, it) } ?: stateMapper.map(stats)
        }
            .onEach { state -> setState { state } }
            .launchIn(viewModelScope)
    }

    @Suppress("EmptyFunctionBlock") // StatisticsIntent has no members: this screen dispatches none
    override suspend fun handle(intent: StatisticsIntent) {
    }
}
