package dev.catsradar.presentation.detail

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.region.EncounterPlace
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.AttachPhoto
import dev.catsradar.domain.usecase.AttachResult
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.ObserveEncounterPlace
import dev.catsradar.domain.usecase.PhotoSource
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.coat.toCatCoat
import dev.catsradar.presentation.runStorageWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

@Suppress("LongParameterList") // one parameter per collaborator; a holder type would exist only to lower the count
class EncounterDetailStore(
    private val encounterId: String,
    observeEncounter: ObserveEncounter,
    observeEncounterPlace: ObserveEncounterPlace,
    private val deleteEncounter: DeleteEncounter,
    private val undoDelete: UndoDelete,
    private val setCoat: SetCoat,
    private val attachPhoto: AttachPhoto,
    private val stateMapper: EncounterDetailStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<EncounterDetailState, EncounterDetailIntent, EncounterDetailEffect>(EncounterDetailState.Loading) {

    private var lastSeen: Encounter? = null
    private var lastPlace: EncounterPlace? = null
    private var deletedHere = false
    private var undoTimeoutJob: Job? = null
    private var attachingPhoto = false

    // Attached but not emitted yet: until it arrives the section keeps showing the attempt, not a bare offer.
    private var arrivingPhotoId: String? = null
    private var awaitingPhoto = false
    private var leaving = false

    init {
        observeEncounter(encounterId)
            .flatMapLatest { encounter -> observeEncounterPlace(encounter).map { place -> encounter to place } }
            .onEach { (encounter, place) ->
                lastSeen = encounter
                lastPlace = place
                val arriving = arrivingPhotoId
                if (encounter == null || encounter.photos.any { it.id == arriving }) arrivingPhotoId = null
                setState { reduce(encounter) }
            }
            .launchIn(viewModelScope)
    }

    // A null emission after our own delete is the delete taking effect, not the encounter vanishing.
    private fun EncounterDetailState.reduce(encounter: Encounter?): EncounterDetailState = when {
        encounter != null -> stateMapper.map(
            encounter,
            clock.today(timeZone),
            attachingPhoto || arrivingPhotoId != null,
            lastPlace,
        )
        deletedHere -> this
        else -> EncounterDetailState.Missing
    }

    override suspend fun handle(intent: EncounterDetailIntent) {
        when (intent) {
            EncounterDetailIntent.BackClicked -> navigateBack()
            EncounterDetailIntent.DeleteClicked -> onDeleteClicked()
            EncounterDetailIntent.UndoClicked -> onUndoClicked()
            // A failed write leaves the shown coat as it was: the flow re-emits the stored value.
            is EncounterDetailIntent.CoatPicked -> runStorageWrite { setCoat(encounterId, intent.coat?.toCatCoat()) }
            EncounterDetailIntent.TakePhotoClicked -> requestPhoto(EncounterDetailEffect.OpenCamera)
            EncounterDetailIntent.PickPhotoClicked -> requestPhoto(EncounterDetailEffect.OpenPhotoPicker)
            is EncounterDetailIntent.PhotoClicked ->
                if ((state.value as? EncounterDetailState.Loaded)?.photos.orEmpty().any { it.id == intent.photoId }) {
                    emit(EncounterDetailEffect.OpenPhoto(intent.photoId))
                }
            EncounterDetailIntent.CoordinatesClicked ->
                if ((state.value as? EncounterDetailState.Loaded)?.onTheMap == true) {
                    emit(EncounterDetailEffect.OpenMap)
                }
            is EncounterDetailIntent.PhotoTaken -> onPhotoChosen(intent.uri, PhotoSource.CAMERA)
            is EncounterDetailIntent.PhotoPicked -> onPhotoChosen(intent.uri, PhotoSource.GALLERY)
        }
    }

    private suspend fun requestPhoto(opener: EncounterDetailEffect) {
        val offered = (state.value as? EncounterDetailState.Loaded)?.addPhoto == AddPhoto.READY
        if (awaitingPhoto || !offered) return
        awaitingPhoto = true
        emit(opener)
    }

    private suspend fun onPhotoChosen(uri: String?, source: PhotoSource) {
        awaitingPhoto = false
        if (uri == null) return
        attachingPhoto = true
        refresh()
        var result: AttachResult? = null
        runStorageWrite { result = attachPhoto(encounterId, uri, source) }
        attachingPhoto = false
        when (val outcome = result) {
            is AttachResult.Attached -> {
                val arrived = lastSeen?.photos.orEmpty().any { it.id == outcome.photoId }
                if (!arrived) arrivingPhotoId = outcome.photoId
                refresh()
            }
            AttachResult.AlreadyThere -> {
                refresh()
                emit(EncounterDetailEffect.PhotoAlreadyThere)
            }
            AttachResult.NotAttachable -> refresh()
            AttachResult.Unreadable, null -> {
                refresh()
                emit(EncounterDetailEffect.PhotoNotAttached)
            }
        }
        if (source == PhotoSource.CAMERA) emit(EncounterDetailEffect.DiscardCapture(uri))
    }

    private fun refresh() {
        setState { if (this is EncounterDetailState.Loaded) reduce(lastSeen) else this }
    }

    private suspend fun onDeleteClicked() {
        // Set before the suspending call: a second tap in flight must see the flag and no-op.
        if (deletedHere || state.value !is EncounterDetailState.Loaded) return
        deletedHere = true
        setState { EncounterDetailState.Deleted(undoVisible = true) }
        startUndoWindow()
        runStorageWrite(onFailure = ::restoreAfterFailedDelete) { deleteEncounter(encounterId) }
    }

    private fun startUndoWindow() {
        undoTimeoutJob?.cancel()
        undoTimeoutJob = viewModelScope.launch {
            delay(Tuning.UNDO_VISIBLE)
            setState { EncounterDetailState.Deleted(undoVisible = false) }
            navigateBack()
        }
    }

    private suspend fun navigateBack() {
        if (leaving) return
        leaving = true
        emit(EncounterDetailEffect.NavigateBack)
    }

    private suspend fun onUndoClicked() {
        val current = state.value
        if (current !is EncounterDetailState.Deleted || !current.undoVisible) return
        undoTimeoutJob?.cancel()
        runStorageWrite(onFailure = { startUndoWindow() }) {
            undoDelete(encounterId)
            deletedHere = false
        }
    }

    private fun restoreAfterFailedDelete() {
        undoTimeoutJob?.cancel()
        deletedHere = false
        setState { reduce(lastSeen) }
    }
}
