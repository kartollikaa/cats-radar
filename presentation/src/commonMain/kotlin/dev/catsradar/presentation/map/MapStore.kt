package dev.catsradar.presentation.map

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

sealed interface MapIntent

sealed interface MapEffect

class MapStore(
    observeEncounters: ObserveEncounters,
    private val stateMapper: MapStateMapper,
) : Store<MapState, MapIntent, MapEffect>(MapState.Loading) {

    init {
        observeEncounters()
            .onEach { encounters -> setState { stateMapper.map(encounters) } }
            .launchIn(viewModelScope)
    }

    @Suppress("EmptyFunctionBlock") // MapIntent has no members: this screen dispatches none
    override suspend fun handle(intent: MapIntent) {
    }
}
