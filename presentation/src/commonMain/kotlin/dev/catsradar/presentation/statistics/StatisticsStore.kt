package dev.catsradar.presentation.statistics

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.ObserveWalkStats
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class StatisticsStore(
    observeStats: ObserveStats,
    observeWalkStats: ObserveWalkStats,
    private val stateMapper: StatisticsStateMapper,
) : Store<StatisticsState, StatisticsIntent, StatisticsEffect>(StatisticsState()) {

    init {
        combine(observeStats(), observeWalkStats()) { stats, walks -> stateMapper.map(stats, walks) }
            .onEach { state -> setState { state } }
            .launchIn(viewModelScope)
    }

    @Suppress("EmptyFunctionBlock") // StatisticsIntent has no members: this screen dispatches none
    override suspend fun handle(intent: StatisticsIntent) {
    }
}
