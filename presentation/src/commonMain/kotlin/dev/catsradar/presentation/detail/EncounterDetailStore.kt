package dev.catsradar.presentation.detail

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.session.OutingWindow
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.AttachPhoto
import dev.catsradar.domain.usecase.AttachResult
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.ObserveEncounterNumber
import dev.catsradar.domain.usecase.ObserveEncounterPlace
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.PhotoSource
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.runStorageWrite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

@Suppress("LongParameterList") // one parameter per collaborator; a holder type would exist only to lower the count
class EncounterDetailStore(
    openedId: String,
    restoredId: String?,
    observeEncounters: ObserveEncounters,
    observeEncounterPlace: ObserveEncounterPlace,
    observeEncounterNumber: ObserveEncounterNumber,
    private val deleteEncounter: DeleteEncounter,
    private val undoDelete: UndoDelete,
    private val attachPhoto: AttachPhoto,
    private val stateMapper: EncounterDetailStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<EncounterDetailState, EncounterDetailIntent, EncounterDetailEffect>(EncounterDetailState.Loading) {

    private val pages = OutingPages(openedId, restoredId)
    private val attempts = PhotoAttempts()
    private var shown: ShownPages? = null

    // The pages the running lookups started from: a later lookup under them must not undo a settle.
    private var lookedUpFor: ShownPages? = null
    private var lookups: Map<String, CatLookup> = emptyMap()

    // Deleted here and not yet undone: the removed state stands, and an emission without this cat is the delete
    // taking effect.
    private var deletedId: String? = null
    private var undoTimeoutJob: Job? = null
    private var awaitingPhoto = false
    private var leaving = false

    init {
        observeEncounters()
            .filter { live -> deletedId.let { it == null || live.holdsLive(it) } }
            .map { live -> pages.update(live) }
            .distinctUntilChanged()
            .flatMapLatest { next ->
                lookUp(next?.window, observeEncounterPlace, observeEncounterNumber).map { found -> next to found }
            }
            .onEach { (next, found) ->
                if (next != lookedUpFor) {
                    lookedUpFor = next
                    shown = next
                }
                if (deletedId == null) lookups = found
                attempts.arrived(next?.window?.cats.orEmpty())
                setState { if (deletedId == null) pagesState() else this }
            }
            .launchIn(viewModelScope)
    }

    private fun pagesState(): EncounterDetailState = shown?.let { onScreen ->
        stateMapper.map(
            onScreen.window,
            currentId = onScreen.currentId,
            today = clock.today(timeZone),
            attaching = attempts.progress,
            lookups = lookups,
        )
    } ?: EncounterDetailState.Missing

    override suspend fun handle(intent: EncounterDetailIntent) {
        when (intent) {
            EncounterDetailIntent.BackClicked -> navigateBack()
            EncounterDetailIntent.DeleteClicked -> onDeleteClicked()
            EncounterDetailIntent.UndoClicked -> onUndoClicked()
            is EncounterDetailIntent.ShowCat -> {
                pages.settle(intent.catId)
                shown = shown?.settledOn(intent.catId)
                refresh()
            }
            is EncounterDetailIntent.TakePhotoClicked ->
                requestPhoto(intent.catId, EncounterDetailEffect.OpenCamera(intent.catId))
            is EncounterDetailIntent.PickPhotoClicked ->
                requestPhoto(intent.catId, EncounterDetailEffect.OpenPhotoPicker(intent.catId))
            is EncounterDetailIntent.PhotoClicked ->
                emitIfOffered(intent.catId, EncounterDetailEffect.OpenPhoto(intent.catId, intent.photoId)) {
                    photos.any { it.id == intent.photoId }
                }
            is EncounterDetailIntent.CoordinatesClicked ->
                emitIfOffered(intent.catId, EncounterDetailEffect.OpenMap(intent.catId)) { mapPosition != null }
            is EncounterDetailIntent.CoatCardClicked ->
                emitIfOffered(intent.catId, EncounterDetailEffect.OpenCoatSheet(intent.catId)) { true }
            is EncounterDetailIntent.SetLocationClicked ->
                emitIfOffered(intent.catId, EncounterDetailEffect.OpenLocationPicker(intent.catId)) { setsLocation }
            is EncounterDetailIntent.PhotoTaken ->
                onPhotosChosen(intent.catId, listOfNotNull(intent.uri), PhotoSource.CAMERA)
            is EncounterDetailIntent.PhotosPicked -> onPhotosChosen(intent.catId, intent.uris, PhotoSource.GALLERY)
        }
    }

    private suspend fun emitIfOffered(catId: String, effect: EncounterDetailEffect, offered: CatPage.() -> Boolean) {
        if (state.value.page(catId)?.offered() == true) emit(effect)
    }

    private suspend fun requestPhoto(catId: String, opener: EncounterDetailEffect) {
        val offered = state.value.page(catId)?.addPhoto == AddPhoto.READY
        if (awaitingPhoto || !offered) return
        awaitingPhoto = true
        emit(opener)
    }

    private suspend fun onPhotosChosen(catId: String, uris: List<String>, source: PhotoSource) {
        awaitingPhoto = false
        if (uris.isEmpty()) return
        // Null for an attempt whose storage write failed.
        val outcomes = mutableListOf<AttachResult?>()
        for (uri in uris) {
            attempts.running(catId, AttachProgress(done = outcomes.size, total = uris.size))
            refresh()
            var result: AttachResult? = null
            runStorageWrite { result = attachPhoto(catId, uri, source) }
            (result as? AttachResult.Attached)?.let { attempts.attached(catId, it.photoId) }
            outcomes += result
        }
        attempts.finished(catId, AttachProgress(done = outcomes.size, total = uris.size))
        refresh()
        outcomes.message()?.let { emit(it) }
        if (source == PhotoSource.CAMERA) uris.forEach { emit(EncounterDetailEffect.DiscardCapture(it)) }
    }

    private fun refresh() {
        setState { if (this is EncounterDetailState.Loaded) pagesState() else this }
    }

    private suspend fun onDeleteClicked() {
        val onScreen = (state.value as? EncounterDetailState.Loaded)?.currentId
        // Set before the suspending call: a second tap in flight must see it and no-op.
        if (deletedId != null || onScreen == null) return
        deletedId = onScreen
        setState { EncounterDetailState.Deleted(undoVisible = true) }
        startUndoWindow()
        val restore = {
            undoTimeoutJob?.cancel()
            deletedId = null
            setState { pagesState() }
        }
        runStorageWrite(onFailure = restore) { deleteEncounter(onScreen) }
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
        val deleted = deletedId
        if (current !is EncounterDetailState.Deleted || !current.undoVisible || deleted == null) return
        undoTimeoutJob?.cancel()
        runStorageWrite(onFailure = { startUndoWindow() }) {
            undoDelete(deleted)
            deletedId = null
            setState { pagesState() }
        }
    }
}

private fun EncounterDetailState.page(catId: String): CatPage? =
    (this as? EncounterDetailState.Loaded)?.pages?.firstOrNull { it.id == catId }

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

private fun lookUp(
    window: OutingWindow?,
    observePlace: ObserveEncounterPlace,
    observeNumber: ObserveEncounterNumber,
): Flow<Map<String, CatLookup>> {
    val cats = window?.cats.orEmpty()
    if (cats.isEmpty()) return flowOf(emptyMap())
    return combine(
        cats.map { cat ->
            combine(observePlace(cat), observeNumber(cat.id)) { place, number -> cat.id to CatLookup(place, number) }
        },
    ) { it.toMap() }
}
