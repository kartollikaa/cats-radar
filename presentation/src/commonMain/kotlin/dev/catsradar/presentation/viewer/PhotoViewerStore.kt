package dev.catsradar.presentation.viewer

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.usecase.GalleryTarget
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.ResolveGalleryLink
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PhotoViewerStore(
    private val encounterId: String,
    observeEncounter: ObserveEncounter,
    private val resolveGalleryLink: ResolveGalleryLink,
    private val stateMapper: PhotoViewerStateMapper,
) : Store<PhotoViewerState, PhotoViewerIntent, PhotoViewerEffect>(PhotoViewerState.Loading) {

    private var closing = false
    private var resolvingGallery = false

    init {
        observeEncounter(encounterId)
            .onEach { encounter ->
                val showing = encounter?.let(stateMapper::map)
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
        if (resolvingGallery) return
        resolvingGallery = true
        try {
            when (val target = resolveGalleryLink(encounterId)) {
                is GalleryTarget.Open -> emit(PhotoViewerEffect.OpenInGallery(target.uri, target.grantRead))
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
