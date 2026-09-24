package dev.catsradar.presentation.map

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.coat.CoatOption
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformWhile
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

sealed interface MapSpotIntent {
    data class CatClicked(val id: String) : MapSpotIntent

    /** "On the map" on an outing's header, naming the outing by one of its cats. */
    data class OutingMapClicked(val encounterId: String) : MapSpotIntent
}

sealed interface MapSpotEffect {
    data class OpenCat(val id: String) : MapSpotEffect

    data class FocusOuting(val encounterId: String) : MapSpotEffect

    data object Close : MapSpotEffect
}

class MapSpotStore(
    catIds: Set<String>,
    coats: Set<CoatOption?>,
    observeEncounters: ObserveEncounters,
    stateMapper: MapSpotStateMapper,
    clock: Clock,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<MapSpotState, MapSpotIntent, MapSpotEffect>(MapSpotState.Loading) {

    init {
        observeEncounters()
            .map { encounters -> stateMapper.map(encounters, catIds, coats, clock.today(timeZone)) }
            // Once every cat is gone the list is over: restoring one later must not reopen it.
            .transformWhile { listed ->
                emit(listed)
                listed != null
            }
            .onEach { listed -> if (listed == null) emit(MapSpotEffect.Close) else setState { listed } }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: MapSpotIntent) {
        when (intent) {
            is MapSpotIntent.CatClicked -> emit(MapSpotEffect.OpenCat(intent.id))
            is MapSpotIntent.OutingMapClicked -> emit(MapSpotEffect.FocusOuting(intent.encounterId))
        }
    }
}
