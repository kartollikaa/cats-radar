package dev.catsradar.presentation.statistics

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.stats.WalkStats
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.ObserveWalkStats
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update

class StatisticsStore(
    observeEncounters: ObserveEncounters,
    observeStats: ObserveStats,
    observeWalkStats: ObserveWalkStats,
    private val stateMapper: StatisticsStateMapper,
) : Store<StatisticsState, StatisticsIntent, StatisticsEffect>(StatisticsState()) {

    private val chart = MutableStateFlow(ChartChoice())

    init {
        val encounters = observeEncounters().shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)
        val walks: Flow<WalkStats?> = flow {
            emit(null)
            emitAll(observeWalkStats(encounters))
        }
        combine(observeStats(encounters), walks, chart) { stats, walk, choice ->
            val mapped = walk?.let { stateMapper.map(stats, it, choice) } ?: stateMapper.map(stats, chart = choice)
            // A day the range stopped showing is let go, so it cannot come back by itself with a wider range.
            val pickedDay = choice.pickedDay
            if (pickedDay != null && mapped.chart.bars.none { it.isPicked && it.epochDay == pickedDay }) {
                chart.update { if (it.pickedDay == pickedDay) it.copy(pickedDay = null) else it }
            }
            mapped
        }
            .onEach { state -> setState { state } }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: StatisticsIntent) {
        when (intent) {
            is StatisticsIntent.RangePicked -> chart.update { it.copy(range = intent.range) }
            is StatisticsIntent.DayPicked -> chart.update { it.copy(pickedDay = intent.epochDay) }
        }
    }
}
