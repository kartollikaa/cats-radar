package dev.catsradar.presentation.viewer

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PhotoViewerStore(
    encounterId: String,
    observeEncounter: ObserveEncounter,
    private val stateMapper: PhotoViewerStateMapper,
) : Store<PhotoViewerState, PhotoViewerIntent, PhotoViewerEffect>(PhotoViewerState.Loading) {

    private var closing = false

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
        }
    }

    private suspend fun close() {
        if (closing) return
        closing = true
        emit(PhotoViewerEffect.Close)
    }
}
