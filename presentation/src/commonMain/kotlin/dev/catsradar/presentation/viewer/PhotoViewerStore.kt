package dev.catsradar.presentation.viewer

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.usecase.GalleryTarget
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.ResolveGalleryLink
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PhotoViewerStore(
    encounterId: String,
    observeEncounter: ObserveEncounter,
    private val resolveGalleryLink: ResolveGalleryLink,
    private val stateMapper: PhotoViewerStateMapper,
) : Store<PhotoViewerState, PhotoViewerIntent, PhotoViewerEffect>(PhotoViewerState.Loading) {

    private var shown: Encounter? = null
    private var closing = false
    private var resolvingGallery = false

    init {
        observeEncounter(encounterId)
            .onEach { encounter ->
                val showing = encounter?.let(stateMapper::map)
                shown = encounter.takeIf { showing != null }
                if (showing != null) setState { showing } else close()
            }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: PhotoViewerIntent) {
        when (intent) {
            PhotoViewerIntent.BackClicked -> close()
            PhotoViewerIntent.OpenInGalleryClicked -> openInGallery()
        }
    }

    private suspend fun openInGallery() {
        val encounter = shown
        if (resolvingGallery || encounter == null) return
        resolvingGallery = true
        try {
            when (val target = resolveGalleryLink(encounter)) {
                is GalleryTarget.Open -> emit(PhotoViewerEffect.OpenInGallery(target.uri))
                GalleryTarget.Gone -> emit(PhotoViewerEffect.GalleryItemGone)
                GalleryTarget.Unavailable -> Unit
            }
        } finally {
            resolvingGallery = false
        }
    }

    private suspend fun close() {
        if (closing) return
        closing = true
        emit(PhotoViewerEffect.Close)
    }
}
