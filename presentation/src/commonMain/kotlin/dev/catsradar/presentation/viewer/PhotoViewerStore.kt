package dev.catsradar.presentation.viewer

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.GalleryTarget
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.RemovePhoto
import dev.catsradar.domain.usecase.ResolveGalleryLink
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.runStorageWrite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList") // one parameter per collaborator, plus the key's two ids
class PhotoViewerStore(
    private val encounterId: String,
    openedOn: String?,
    observeEncounter: ObserveEncounter,
    private val removePhoto: RemovePhoto,
    private val resolveGalleryLink: ResolveGalleryLink,
    private val stateMapper: PhotoViewerStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<PhotoViewerState, PhotoViewerIntent, PhotoViewerEffect>(PhotoViewerState.Loading) {

    private var shown: List<EncounterPhoto> = emptyList()
    private var galleryTargets: Map<String, GalleryTarget> = emptyMap()
    private var closing = false
    private var resolvingGallery = false
    private var removing = false

    init {
        observeEncounter(encounterId)
            .onEach { encounter ->
                val showing = encounter?.let { stateMapper.map(it, clock.today(timeZone), openedOn, galleryTargets) }
                shown = encounter?.photos.orEmpty()
                if (showing != null) setState { showing } else close()
            }
            .mapLatest { encounter -> encounter?.photos.orEmpty().associate { it.id to resolveGalleryLink(it) } }
            .onEach(::offerGallery)
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: PhotoViewerIntent) {
        when (intent) {
            PhotoViewerIntent.BackClicked -> close()
            is PhotoViewerIntent.OpenInGalleryClicked -> openInGallery(intent.photoId)
            is PhotoViewerIntent.RemovePhotoClicked -> requestRemoval(intent.photoId)
            PhotoViewerIntent.RemovePhotoCancelled -> cancelRemoval()
            PhotoViewerIntent.RemovePhotoConfirmed -> confirmRemoval()
        }
    }

    private fun requestRemoval(photoId: String) {
        if (removing || shown.none { it.id == photoId }) return
        setState { (this as? PhotoViewerState.Showing)?.copy(removingPhotoId = photoId) ?: this }
    }

    private fun cancelRemoval() {
        if (removing) return
        setState { (this as? PhotoViewerState.Showing)?.copy(removingPhotoId = null) ?: this }
    }

    private suspend fun confirmRemoval() {
        val photoId = (state.value as? PhotoViewerState.Showing)?.removingPhotoId ?: return
        if (removing) return
        removing = true
        setState { (this as? PhotoViewerState.Showing)?.copy(removalInFlight = true) ?: this }
        var failed = false
        var removed = false
        try {
            runStorageWrite(onFailure = { failed = true }) {
                removed = removePhoto(encounterId, photoId)
            }
        } finally {
            removing = false
            setState {
                (this as? PhotoViewerState.Showing)?.copy(
                    removingPhotoId = null,
                    removalInFlight = false,
                ) ?: this
            }
        }
        if (failed || !removed) emit(PhotoViewerEffect.RemovePhotoFailed)
    }

    private suspend fun openInGallery(photoId: String) {
        val photo = shown.firstOrNull { it.id == photoId }
        if (resolvingGallery || photo == null) return
        resolvingGallery = true
        try {
            when (val target = resolveGalleryLink(photo)) {
                is GalleryTarget.Open -> emit(PhotoViewerEffect.OpenInGallery(target.uri))
                GalleryTarget.Gone -> {
                    offerGallery(galleryTargets + (photoId to GalleryTarget.Gone))
                    emit(PhotoViewerEffect.GalleryItemGone)
                }
                GalleryTarget.Unavailable -> Unit
            }
        } finally {
            resolvingGallery = false
        }
    }

    private fun offerGallery(targets: Map<String, GalleryTarget>) {
        galleryTargets = targets
        setState { (this as? PhotoViewerState.Showing)?.let { stateMapper.offerGallery(it, targets) } ?: this }
    }

    private suspend fun close() {
        if (closing) return
        closing = true
        emit(PhotoViewerEffect.Close)
    }
}
