package dev.catsradar.presentation.counter

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveEncounterCount
import dev.catsradar.domain.usecase.UndoLastTally
import dev.catsradar.presentation.Store
import kotlinx.coroutines.CancellationException
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
) : Store<CounterState, CounterIntent, CounterEffect>(stateMapper.map(count = 0, undoVisible = false)) {

    private var tapSequence = 0
    private var undoTargetSequence = -1
    private var undoTargetId: String? = null
    private var undoTimeoutJob: Job? = null
    private var locationPermissionRequested = false

    init {
        observeEncounterCount()
            .onEach { count ->
                setState {
                    stateMapper.map(
                        count = count,
                        undoVisible = undoVisible,
                        locationPermissionHintVisible = locationPermissionHintVisible,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: CounterIntent) {
        when (intent) {
            CounterIntent.TallyClicked -> onTallyClicked()
            CounterIntent.UndoClicked -> onUndoClicked()
            is CounterIntent.LocationPermissionResult -> onLocationPermissionResult(intent.granted)
            CounterIntent.GrantLocationClicked -> emit(CounterEffect.RequestLocationPermission)
            CounterIntent.LocationPermissionHintDismissed -> setState { copy(locationPermissionHintVisible = false) }
        }
    }

    private suspend fun onTallyClicked() {
        val sequence = ++tapSequence
        // The tap must feel instant: the tick fires before the write, not after it succeeds.
        emit(CounterEffect.HapticTick)
        // Only the very first tally ever opens the system dialog; a denial must not re-prompt on
        // every later tap.
        if (!locationPermissionRequested) {
            locationPermissionRequested = true
            emit(CounterEffect.RequestLocationPermission)
        }
        runWriteIgnoringFailure {
            val encounter = logTally()
            emit(CounterEffect.AttachLocation(encounter.id))
            // Captured before suspending, not completion order: a later tap's insert can resume
            // before an earlier one's, so only a higher sequence may overwrite the undo target.
            if (sequence > undoTargetSequence) {
                undoTargetSequence = sequence
                undoTargetId = encounter.id
                setState { copy(undoVisible = true) }
                restartUndoTimer()
            }
        }
    }

    private fun onLocationPermissionResult(granted: Boolean) {
        setState { copy(locationPermissionHintVisible = !granted) }
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
        // Read-and-clear before the suspending call below, so a second dispatch sees null and
        // no-ops: that ordering is what makes pressing undo twice delete only once.
        val id = undoTargetId ?: return
        undoTargetId = null
        undoTimeoutJob?.cancel()
        setState { copy(undoVisible = false) }
        runWriteIgnoringFailure { undoLastTally(id) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // no user-visible error handling this slice
    private suspend fun runWriteIgnoringFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed insert or delete must not crash the app.
        }
    }
}
