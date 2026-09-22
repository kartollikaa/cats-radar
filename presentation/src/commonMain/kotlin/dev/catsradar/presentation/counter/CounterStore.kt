package dev.catsradar.presentation.counter

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveEncounterCount
import dev.catsradar.domain.usecase.UndoLastTally
import dev.catsradar.presentation.Store
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CounterStore(
    private val logTally: LogTally,
    private val undoLastTally: UndoLastTally,
    observeEncounterCount: ObserveEncounterCount,
    private val stateMapper: CounterStateMapper,
    private val haptics: Haptics,
) : Store<CounterState, CounterIntent, CounterEffect>(stateMapper.map(count = 0, undoVisible = false)) {

    private var undoTargetId: String? = null
    private var undoTimeoutJob: Job? = null

    init {
        observeEncounterCount()
            .onEach { count -> setState { stateMapper.map(count = count, undoVisible = undoVisible) } }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: CounterIntent) {
        when (intent) {
            CounterIntent.TallyClicked -> onTallyClicked()
            CounterIntent.UndoClicked -> onUndoClicked()
        }
    }

    private suspend fun onTallyClicked() {
        val encounter = logTally()
        undoTargetId = encounter.id
        setState { copy(undoVisible = true) }
        restartUndoTimer()
        haptics.tick()
    }

    private fun restartUndoTimer() {
        undoTimeoutJob?.cancel()
        undoTimeoutJob = viewModelScope.launch {
            delay(Tuning.UNDO_VISIBLE)
            undoTargetId = null
            setState { copy(undoVisible = false) }
        }
    }

    private suspend fun onUndoClicked() {
        val id = undoTargetId ?: return
        undoTargetId = null
        undoTimeoutJob?.cancel()
        setState { copy(undoVisible = false) }
        undoLastTally(id)
    }
}
