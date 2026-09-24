package dev.catsradar.presentation.counter

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.platform.LocationPermissionRequestState
import dev.catsradar.domain.repository.ReportedJob
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.usecase.LogPhoto
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.ObserveWalkElapsed
import dev.catsradar.domain.usecase.PhotoResult
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.domain.usecase.UndoImport
import dev.catsradar.domain.usecase.UndoLastTally
import dev.catsradar.presentation.ReportedRun
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.coat.toCatCoat
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.runStorageWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@Suppress("LongParameterList") // one parameter per collaborator
class CounterStore(
    private val logTally: LogTally,
    private val logPhoto: LogPhoto,
    private val undoLastTally: UndoLastTally,
    private val undoImport: UndoImport,
    private val setCoat: SetCoat,
    observeStats: ObserveStats,
    observeWalkElapsed: ObserveWalkElapsed,
    private val settingsRepository: SettingsRepository,
    private val stateMapper: CounterStateMapper,
    private val locationPermissionRequestState: LocationPermissionRequestState,
) : Store<CounterState, CounterIntent, CounterEffect>(stateMapper.initial()) {

    private var tapSequence = 0
    private val tapsBeingWritten = mutableSetOf<Int>()
    private val undoneWhileWriting = mutableSetOf<Int>()
    private val undoableRun = mutableListOf<UndoableTally>()
    private var expiredThroughSequence = 0
    private var undoTimeoutJob: Job? = null
    private val runTapsBeingWritten: List<Int>
        get() = tapsBeingWritten.filter { it > expiredThroughSequence && it !in undoneWhileWriting }

    // Not in State: the screen shows how many were added, never which ones.
    private var importedIds: List<String> = emptyList()
    private val importRun = ReportedRun(settingsRepository, ReportedJob.GALLERY_IMPORT)
    private var importSummaryTimeoutJob: Job? = null
    private var coatPromptEncounterId: String? = null

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
                        lastCoat = lastCoat,
                        walkingMode = walkingMode,
                        walkElapsedLabel = walkElapsedLabel,
                        importProgress = importProgress,
                        importSummary = importSummary,
                        coatPrompt = coatPrompt,
                    )
                }
                announceMilestone(stats.total)
            }
            .launchIn(viewModelScope)
        settingsRepository.walkingMode()
            // Nothing ticks the walk's clock while walking mode is off.
            .flatMapLatest { enabled ->
                val elapsed = if (enabled) observeWalkElapsed().onStart { emit(null) } else flowOf(null)
                elapsed.map { enabled to stateMapper.walkElapsedLabel(enabled, it) }
            }
            .onEach { (enabled, elapsedLabel) ->
                setState { copy(walkingMode = enabled, walkElapsedLabel = elapsedLabel) }
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
            // Only the flag is written; the notification follows it from outside the screen.
            is CounterIntent.WalkingModeToggled ->
                runStorageWrite { settingsRepository.setWalkingMode(intent.enabled) }
            is CounterIntent.Import -> handleImport(intent)
            is CounterIntent.CoatTallyClicked -> onTallyClicked(intent.coat.toCatCoat())
            is CounterIntent.CoatPromptPicked, CounterIntent.CoatPromptDismissed -> {
                val encounterId = coatPromptEncounterId
                // Closed before the write: the prompt never waits on storage, and a failed write still closes it.
                coatPromptEncounterId = null
                setState { copy(coatPrompt = null) }
                if (intent is CounterIntent.CoatPromptPicked && encounterId != null) {
                    runStorageWrite { setCoat(encounterId, intent.coat.toCatCoat()) }
                }
            }
            is CounterIntent.LocationPermissionResult ->
                setState { copy(locationPermissionHintVisible = !intent.granted) }
            CounterIntent.GrantLocationClicked -> emit(CounterEffect.RequestLocationPermission)
            CounterIntent.LocationPermissionHintDismissed -> setState { copy(locationPermissionHintVisible = false) }
        }
    }

    private suspend fun onTallyClicked(coat: CatCoat? = null) {
        val sequence = ++tapSequence
        tapsBeingWritten += sequence
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
        runStorageWrite {
            val encounter = logTally(coat)
            // Settled before anything suspends: an Undo pressed meanwhile must find the tap in the run.
            tapsBeingWritten -= sequence
            if (undoneWhileWriting.remove(sequence)) {
                undoLastTally(encounter.id)
                return@runStorageWrite
            }
            // A slow write from a run that has already expired must not reopen the window.
            if (sequence > expiredThroughSequence) {
                val tally = UndoableTally(sequence, encounter.id, coat?.toOption())
                undoableRun += tally
                // By tap, not by completion: a later tap's insert can resume before an earlier one's.
                undoableRun.sortBy { it.sequence }
                if (undoableRun.last() === tally) showNewestUndoable()
            }
            emit(CounterEffect.AttachLocation(encounter.id))
        }
        tapsBeingWritten -= sequence
        undoneWhileWriting -= sequence
        showBurst()
    }

    private suspend fun onPhotoCaptured(uri: String?) {
        // A cancelled camera is not a failure and must leave nothing behind.
        if (uri == null) return
        runStorageWrite {
            when (val result = logPhoto(uri)) {
                is PhotoResult.Logged -> {
                    coatPromptEncounterId = result.encounter.id
                    setState { copy(coatPrompt = stateMapper.coatPrompt(result.encounter)) }
                    if (result.needsLocation) emit(CounterEffect.AttachLocation(result.encounter.id))
                }
                PhotoResult.Unreadable -> emit(CounterEffect.PhotoNotSaved)
            }
            // Whatever the outcome, the full-size original has served its purpose; leaving it
            // would grow the cache by one photo per cat.
            emit(CounterEffect.DiscardCapture(uri))
        }
    }

    private suspend fun handleImport(intent: CounterIntent.Import) {
        when (intent) {
            CounterIntent.Import.Requested -> emit(CounterEffect.PickPhotos)
            // A dismissed picker is not an import: no progress row, no summary, nothing to undo.
            is CounterIntent.Import.PhotosPicked -> if (intent.uris.isNotEmpty()) {
                setState {
                    copy(
                        importProgress = ImportProgressState(done = 0, total = intent.uris.size),
                        importSummary = null,
                    )
                }
                emit(CounterEffect.StartImport(intent.uris))
            }
            is CounterIntent.Import.Progressed -> setState {
                copy(importProgress = ImportProgressState(done = intent.done, total = intent.total))
            }
            is CounterIntent.Import.Finished -> if (importRun.claim(intent.runId)) {
                importedIds = intent.addedIds
                setState {
                    copy(
                        importProgress = null,
                        importSummary = stateMapper.importSummary(
                            addedCount = intent.addedIds.size,
                            skipped = intent.skipped,
                            failed = intent.failed,
                        ),
                    )
                }
                importSummaryTimeoutJob?.cancel()
                importSummaryTimeoutJob = viewModelScope.launch {
                    delay(Tuning.IMPORT_SUMMARY_VISIBLE)
                    importedIds = emptyList()
                    setState { copy(importSummary = null) }
                    importRun.acknowledge(intent.runId)
                }
            }
            CounterIntent.Import.UndoClicked -> onUndoImportClicked()
            CounterIntent.Import.SummaryDismissed -> {
                setState { copy(importSummary = null) }
                importRun.acknowledge()
            }
        }
    }

    private suspend fun onUndoImportClicked() {
        // Read-and-clear first: a second tap finds nothing and no-ops, so the same cats are
        // never soft-deleted twice.
        val ids = importedIds
        if (ids.isEmpty()) return
        importedIds = emptyList()
        val runId = importRun.reportedId
        setState { copy(importSummary = importSummary?.copy(undoable = false)) }
        // The batch write is all or none, so a failed one left every cat in place and can be retried.
        runStorageWrite(onFailure = { restoreUndoImport(ids) }) {
            undoImport(ids)
            importRun.acknowledge(runId)
        }
    }

    private fun restoreUndoImport(ids: List<String>) {
        if (importedIds.isNotEmpty() || state.value.importSummary == null) return
        importedIds = ids
        setState { copy(importSummary = importSummary?.copy(undoable = true)) }
    }

    private fun showBurst() {
        val cats = undoableRun.size + runTapsBeingWritten.size
        setState { copy(tapBurst = cats.takeIf { it > 0 }) }
    }

    private fun showNewestUndoable() {
        undoTimeoutJob?.cancel()
        val newest = undoableRun.lastOrNull()
        if (newest == null) {
            setState { copy(undoVisible = false, lastCoat = null) }
            return
        }
        setState { copy(undoVisible = true, lastCoat = newest.coat) }
        undoTimeoutJob = viewModelScope.launch {
            delay(Tuning.UNDO_VISIBLE)
            expiredThroughSequence = newest.sequence
            undoableRun.clear()
            setState { copy(undoVisible = false, lastCoat = null) }
            showBurst()
        }
    }

    private suspend fun onUndoClicked() {
        val newestBeingWritten = runTapsBeingWritten.maxOrNull()
        if (newestBeingWritten != null && newestBeingWritten > (undoableRun.lastOrNull()?.sequence ?: 0)) {
            // The store learns its id only when the insert returns, so the tap is taken back as it lands.
            undoneWhileWriting += newestBeingWritten
            showNewestUndoable()
            showBurst()
            return
        }
        // Taken off before the suspending calls below, so an undo dispatched right behind this one
        // takes the next cat back rather than this one a second time.
        val undone = undoableRun.removeLastOrNull() ?: return
        showNewestUndoable()
        showBurst()
        emit(CounterEffect.CancelLocationAttach(undone.encounterId))
        runStorageWrite { undoLastTally(undone.encounterId) }
    }

    private class UndoableTally(val sequence: Int, val encounterId: String, val coat: CoatOption?)
}
