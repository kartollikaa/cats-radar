package dev.catsradar.presentation.detail

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.coat.toCatCoat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

@Suppress("LongParameterList") // one parameter per collaborator; a holder type would exist only to lower the count
class EncounterDetailStore(
    private val encounterId: String,
    observeEncounter: ObserveEncounter,
    private val deleteEncounter: DeleteEncounter,
    private val undoDelete: UndoDelete,
    private val setCoat: SetCoat,
    private val stateMapper: EncounterDetailStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<EncounterDetailState, EncounterDetailIntent, EncounterDetailEffect>(EncounterDetailState.Loading) {

    private var lastSeen: Encounter? = null
    private var deletedHere = false
    private var undoTimeoutJob: Job? = null

    init {
        observeEncounter(encounterId)
            .onEach { encounter ->
                lastSeen = encounter
                setState { reduce(encounter) }
            }
            .launchIn(viewModelScope)
    }

    // A null emission after our own delete is the delete taking effect, not the encounter vanishing.
    private fun EncounterDetailState.reduce(encounter: Encounter?): EncounterDetailState = when {
        encounter != null -> stateMapper.map(encounter, clock.today(timeZone))
        deletedHere -> this
        else -> EncounterDetailState.Missing
    }

    override suspend fun handle(intent: EncounterDetailIntent) {
        when (intent) {
            EncounterDetailIntent.DeleteClicked -> onDeleteClicked()
            EncounterDetailIntent.UndoClicked -> onUndoClicked()
            is EncounterDetailIntent.CoatPicked -> onCoatPicked(intent.coat)
        }
    }

    private suspend fun onDeleteClicked() {
        // Set before the suspending call: a second tap in flight must see the flag and no-op.
        if (deletedHere || state.value !is EncounterDetailState.Loaded) return
        deletedHere = true
        setState { EncounterDetailState.Deleted(undoVisible = true) }
        startUndoWindow()
        runWrite(onFailure = ::restoreAfterFailedDelete) { deleteEncounter(encounterId) }
    }

    private fun startUndoWindow() {
        undoTimeoutJob?.cancel()
        undoTimeoutJob = viewModelScope.launch {
            delay(Tuning.UNDO_VISIBLE)
            setState { EncounterDetailState.Deleted(undoVisible = false) }
            emit(EncounterDetailEffect.NavigateBack)
        }
    }

    private suspend fun onUndoClicked() {
        val current = state.value
        if (current !is EncounterDetailState.Deleted || !current.undoVisible) return
        undoTimeoutJob?.cancel()
        runWrite(onFailure = { startUndoWindow() }) {
            undoDelete(encounterId)
            deletedHere = false
        }
    }

    private suspend fun onCoatPicked(coat: CoatOption?) {
        // A failed write leaves the shown coat as it was: the flow re-emits the stored value.
        runWrite(onFailure = {}) { setCoat(encounterId, coat?.toCatCoat()) }
    }

    private fun restoreAfterFailedDelete() {
        undoTimeoutJob?.cancel()
        deletedHere = false
        setState { reduce(lastSeen) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // any storage failure degrades the same way
    private suspend fun runWrite(onFailure: () -> Unit, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure()
        }
    }
}
