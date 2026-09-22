package dev.catsradar.presentation.counter

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.platform.LocationPermissionRequestState
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.usecase.LogPhoto
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.PhotoResult
import dev.catsradar.domain.usecase.UndoLastTally
import dev.catsradar.presentation.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Suppress("LongParameterList") // one parameter per collaborator
class CounterStore(
    private val logTally: LogTally,
    private val logPhoto: LogPhoto,
    private val undoLastTally: UndoLastTally,
    observeStats: ObserveStats,
    private val settingsRepository: SettingsRepository,
    private val stateMapper: CounterStateMapper,
    private val locationPermissionRequestState: LocationPermissionRequestState,
) : Store<CounterState, CounterIntent, CounterEffect>(stateMapper.map(count = 0, undoVisible = false)) {

    private var tapSequence = 0
    private var undoTargetSequence = -1
    private var undoTargetId: String? = null
    private var undoTimeoutJob: Job? = null
    private var burstJob: Job? = null

    init {
        observeStats()
            .onEach { stats ->
                setState {
                    stateMapper.map(
                        count = stats.total,
                        undoVisible = undoVisible,
                        locationPermissionHintVisible = locationPermissionHintVisible,
                        currentOuting = stats.currentOuting,
                        tapBurst = tapBurst,
                    )
                }
                announceMilestone(stats.total)
            }
            .launchIn(viewModelScope)
    }

    private suspend fun announceMilestone(total: Int) {
        val reached = Tuning.MILESTONES.filter { it <= total }.maxOrNull() ?: return
        if (reached <= settingsRepository.lastSeenMilestone().first()) return
        // Persisted before the effect: a process death between the two would otherwise celebrate
        // the same milestone again on the next launch.
        settingsRepository.setLastSeenMilestone(reached)
        emit(CounterEffect.MilestoneReached(reached))
    }

    override suspend fun handle(intent: CounterIntent) {
        when (intent) {
            CounterIntent.TallyClicked -> onTallyClicked()
            CounterIntent.CameraClicked -> emit(CounterEffect.OpenCamera)
            is CounterIntent.PhotoCaptured -> onPhotoCaptured(intent.uri)
            CounterIntent.UndoClicked -> onUndoClicked()
            is CounterIntent.LocationPermissionResult -> onLocationPermissionResult(intent.granted)
            CounterIntent.GrantLocationClicked -> emit(CounterEffect.RequestLocationPermission)
            CounterIntent.LocationPermissionHintDismissed -> setState { copy(locationPermissionHintVisible = false) }
        }
    }

    private suspend fun onTallyClicked() {
        val sequence = ++tapSequence
        // The tap must feel instant: the tick and the "+N" land before the write, not after it
        // succeeds, so holding the button down still counts up smoothly.
        emit(CounterEffect.HapticTick)
        showBurst()
        // Only the very first tally ever opens the system dialog; a denial must not re-prompt on
        // every later tap, even across a process death (the flag is persisted, not in-memory).
        if (!locationPermissionRequestState.alreadyRequested) {
            locationPermissionRequestState.markRequested()
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

    private suspend fun onPhotoCaptured(uri: String?) {
        // A cancelled camera is not a failure and must leave nothing behind.
        if (uri == null) return
        runWriteIgnoringFailure {
            when (val result = logPhoto(uri)) {
                is PhotoResult.Logged ->
                    if (result.needsLocation) emit(CounterEffect.AttachLocation(result.encounter.id))
                PhotoResult.Unreadable -> emit(CounterEffect.PhotoNotSaved)
            }
            // Whatever the outcome, the full-size original has served its purpose; leaving it
            // would grow the cache by one photo per cat.
            emit(CounterEffect.DiscardCapture(uri))
        }
    }

    private fun onLocationPermissionResult(granted: Boolean) {
        setState { copy(locationPermissionHintVisible = !granted) }
    }

    private fun showBurst() {
        setState { copy(tapBurst = (tapBurst ?: 0) + 1) }
        burstJob?.cancel()
        burstJob = viewModelScope.launch {
            delay(Tuning.TAP_BURST_VISIBLE)
            setState { copy(tapBurst = null) }
        }
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
        emit(CounterEffect.CancelLocationAttach(id))
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
