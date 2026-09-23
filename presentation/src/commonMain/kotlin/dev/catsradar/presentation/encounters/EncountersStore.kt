package dev.catsradar.presentation.encounters

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.DeletedBatch
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.DeleteEncounters
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.UndoDeleteEncounters
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.runStorageWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

@Suppress("LongParameterList") // one parameter per collaborator; a holder type would exist only to lower the count
class EncountersStore(
    observeEncounters: ObserveEncounters,
    settingsRepository: SettingsRepository,
    private val deleteEncounters: DeleteEncounters,
    private val undoDeleteEncounters: UndoDeleteEncounters,
    private val stateMapper: EncountersStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<EncountersState, EncountersIntent, EncountersEffect>(EncountersState()) {

    private var deleting = false
    private var undoable: DeletedBatch? = null
    private var undoTimeoutJob: Job? = null

    init {
        combine(observeEncounters(), settingsRepository.encountersGrid()) { encounters, grid -> encounters to grid }
            .onEach { (encounters, grid) ->
                setState {
                    stateMapper.map(encounters, clock.today(timeZone), grid, selectedIds)
                        .copy(removedCount = removedCount)
                }
            }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: EncountersIntent) {
        when (intent) {
            is EncountersIntent.EncounterClicked -> onEncounterClicked(intent.id)
            is EncountersIntent.EncounterLongPressed -> toggle(intent.id)
            EncountersIntent.SelectionDismissed -> setState { withSelection(emptySet()) }
            EncountersIntent.DeleteSelectedClicked -> onDeleteSelectedClicked()
            EncountersIntent.UndoClicked -> onUndoClicked()
        }
    }

    private suspend fun onEncounterClicked(id: String) {
        if (state.value.isSelecting) toggle(id) else emit(EncountersEffect.OpenEncounter(id))
    }

    private fun toggle(id: String) {
        setState { withSelection(if (id in selectedIds) selectedIds - id else selectedIds + id) }
    }

    private suspend fun onDeleteSelectedClicked() {
        val ids = state.value.selectedIds
        // Set before the suspending write: a second tap in flight must see the flag and no-op.
        if (deleting || ids.isEmpty()) return
        deleting = true
        try {
            runStorageWrite {
                val batch = deleteEncounters(ids)
                setState { withSelection(selectedIds - ids) }
                offerUndo(batch)
            }
        } finally {
            deleting = false
        }
    }

    private fun offerUndo(batch: DeletedBatch) {
        undoable = batch
        setState { copy(removedCount = batch.ids.size) }
        undoTimeoutJob?.cancel()
        undoTimeoutJob = viewModelScope.launch {
            delay(Tuning.UNDO_VISIBLE)
            undoable = null
            setState { copy(removedCount = null) }
        }
    }

    private suspend fun onUndoClicked() {
        val batch = undoable ?: return
        undoable = null
        undoTimeoutJob?.cancel()
        setState { copy(removedCount = null) }
        // A batch deleted while this write was in flight owns the bar now; a failure must not take it back.
        runStorageWrite(onFailure = { if (undoable == null) offerUndo(batch) }) { undoDeleteEncounters(batch) }
    }
}
