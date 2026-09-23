package dev.catsradar.presentation.encounters

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.DeletedBatch
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.DeleteEncounters
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.UndoDeleteEncounters
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.runStorageWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

class EncountersStore(
    observeEncounters: ObserveEncounters,
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
        observeEncounters()
            .onEach { encounters ->
                setState {
                    stateMapper.map(encounters, clock.today(timeZone), selectedIds).copy(removedCount = removedCount)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun EncountersState.reselect(selectedIds: Set<String>): EncountersState =
        stateMapper.select(rows, selectedIds).copy(removedCount = removedCount)

    override suspend fun handle(intent: EncountersIntent) {
        when (intent) {
            is EncountersIntent.RowClicked -> onRowClicked(intent.id)
            is EncountersIntent.RowLongPressed -> toggle(intent.id)
            EncountersIntent.SelectionDismissed -> setState { reselect(emptySet()) }
            EncountersIntent.DeleteSelectedClicked -> onDeleteSelectedClicked()
            EncountersIntent.UndoClicked -> onUndoClicked()
        }
    }

    private suspend fun onRowClicked(id: String) {
        if (state.value.isSelecting) toggle(id) else emit(EncountersEffect.OpenEncounter(id))
    }

    private fun toggle(id: String) {
        setState { reselect(if (id in selectedIds) selectedIds - id else selectedIds + id) }
    }

    private suspend fun onDeleteSelectedClicked() {
        val ids = state.value.selectedIds
        // Set before the suspending write: a second tap in flight must see the flag and no-op.
        if (deleting || ids.isEmpty()) return
        deleting = true
        try {
            runStorageWrite {
                val batch = deleteEncounters(ids)
                setState { reselect(selectedIds - ids) }
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
