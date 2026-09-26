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
    private var attachingPhotos = false
    private var attachProgress = AttachProgress(done = 0, total = 0)

    // Attached but not emitted yet: until they arrive the section keeps showing the attempt, not a bare offer.
    private val arrivingPhotoIds = mutableSetOf<String>()
    private var awaitingPhoto = false
    private var leaving = false

    init {
        observeEncounter(encounterId)
            .flatMapLatest { encounter -> observeEncounterPlace(encounter).map { place -> encounter to place } }
            .onEach { (encounter, place) ->
                lastSeen = encounter
                lastPlace = place
                if (encounter == null) arrivingPhotoIds.clear() else arrivingPhotoIds -= encounter.photoIds()
                setState { reduce(encounter) }
            }
            .launchIn(viewModelScope)
    }

    // A null emission after our own delete is the delete taking effect, not the encounter vanishing.
    private fun EncounterDetailState.reduce(encounter: Encounter?): EncounterDetailState = when {
        encounter != null -> stateMapper.map(
            encounter,
            clock.today(timeZone),
            attachProgress.takeIf { attachingPhotos || arrivingPhotoIds.isNotEmpty() },
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
            is EncounterDetailIntent.CoatPicked -> runStorageWrite { setCoat(intent.catId, intent.coat?.toCatCoat()) }
            is EncounterDetailIntent.TakePhotoClicked -> requestPhoto(EncounterDetailEffect.OpenCamera(intent.catId))
            is EncounterDetailIntent.PickPhotoClicked ->
                requestPhoto(EncounterDetailEffect.OpenPhotoPicker(intent.catId))
            is EncounterDetailIntent.PhotoClicked ->
                emitIfShownAndOffered(intent.catId, EncounterDetailEffect.OpenPhoto(intent.catId, intent.photoId)) {
                    photos.any { it.id == intent.photoId }
                }
            is EncounterDetailIntent.CoordinatesClicked ->
                emitIfShownAndOffered(
                    intent.catId,
                    EncounterDetailEffect.OpenMap(intent.catId),
                ) { mapPosition != null }
            is EncounterDetailIntent.SetLocationClicked ->
                emitIfShownAndOffered(
                    intent.catId,
                    EncounterDetailEffect.OpenLocationPicker(intent.catId),
                ) { setsLocation }
            is EncounterDetailIntent.PhotoTaken ->
                onPhotosChosen(intent.catId, listOfNotNull(intent.uri), PhotoSource.CAMERA)
            is EncounterDetailIntent.PhotosPicked -> onPhotosChosen(intent.catId, intent.uris, PhotoSource.GALLERY)
        }
    }

    private suspend fun emitIfShownAndOffered(
        catId: String,
        effect: EncounterDetailEffect,
        offered: EncounterDetailState.Loaded.() -> Boolean,
    ) {
        if (catId == encounterId && (state.value as? EncounterDetailState.Loaded)?.offered() == true) emit(effect)
    }

    private suspend fun requestPhoto(opener: EncounterDetailEffect) {
        val offered = (state.value as? EncounterDetailState.Loaded)?.addPhoto == AddPhoto.READY
        if (awaitingPhoto || !offered) return
        awaitingPhoto = true
        emit(opener)
    }

    private suspend fun onPhotosChosen(catId: String, uris: List<String>, source: PhotoSource) {
        awaitingPhoto = false
        if (uris.isEmpty()) return
        attachingPhotos = true
        // Null for an attempt whose storage write failed.
        val outcomes = mutableListOf<AttachResult?>()
        for (uri in uris) {
            attachProgress = AttachProgress(done = outcomes.size, total = uris.size)
            refresh()
            var result: AttachResult? = null
            runStorageWrite { result = attachPhoto(catId, uri, source) }
            val attached = result as? AttachResult.Attached
            if (attached != null && attached.photoId !in lastSeen?.photoIds().orEmpty()) {
                arrivingPhotoIds += attached.photoId
            }
            outcomes += result
        }
        attachProgress = AttachProgress(done = outcomes.size, total = uris.size)
        attachingPhotos = false
        refresh()
        outcomes.message()?.let { emit(it) }
        if (source == PhotoSource.CAMERA) uris.forEach { emit(EncounterDetailEffect.DiscardCapture(it)) }
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
        val restore = {
            undoTimeoutJob?.cancel()
            deletedHere = false
            setState { reduce(lastSeen) }
        }
        runStorageWrite(onFailure = restore) { deleteEncounter(encounterId) }
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
}

private fun Encounter.photoIds(): Set<String> = photos.mapTo(mutableSetOf()) { it.id }

// A cat removed mid-pick answers NotAttachable, which says nothing: the screen already shows it gone.
private fun List<AttachResult?>.message(): EncounterDetailEffect? {
    val notAttached = count { it == null || it == AttachResult.Unreadable }
    return when {
        notAttached == 1 -> EncounterDetailEffect.PhotoNotAttached
        notAttached > 1 -> EncounterDetailEffect.PhotosNotAttached(notAttached)
        any { it != AttachResult.AlreadyThere } -> null
        size == 1 -> EncounterDetailEffect.PhotoAlreadyThere
        else -> EncounterDetailEffect.PhotosAlreadyThere
    }
}
