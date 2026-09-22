package dev.catsradar.presentation.statistics

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class StatisticsStore(
    observeStats: ObserveStats,
    private val stateMapper: StatisticsStateMapper,
) : Store<StatisticsState, StatisticsIntent, StatisticsEffect>(StatisticsState()) {

    init {
        observeStats()
            .onEach { stats -> setState { stateMapper.map(stats) } }
            .launchIn(viewModelScope)
    }

    @Suppress("EmptyFunctionBlock") // StatisticsIntent has no members: this screen dispatches none
    override suspend fun handle(intent: StatisticsIntent) {
    }
}
