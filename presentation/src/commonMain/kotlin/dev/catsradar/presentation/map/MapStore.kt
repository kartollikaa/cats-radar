package dev.catsradar.presentation.map

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

sealed interface MapIntent {
    data class CatsTapped(val ids: List<String>) : MapIntent

    data object SpotDismissed : MapIntent

    data class OutingFocused(val encounterId: String) : MapIntent

    data object FocusCleared : MapIntent
}

sealed interface MapEffect {
    data class OpenCat(val id: String) : MapEffect
}

class MapStore(
    observeEncounters: ObserveEncounters,
    private val stateMapper: MapStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<MapState, MapIntent, MapEffect>(MapState.Loading) {

    private val openSpot = MutableStateFlow<Set<String>?>(null)
    private val focus = MutableStateFlow<String?>(null)

    init {
        combine(observeEncounters(), openSpot, focus) { encounters, spot, outing ->
            val mapped = stateMapper.map(encounters, clock.today(timeZone), spot, outing)
            val shown = mapped as? MapState.Located
            // A spot or focus the cats no longer match is let go, so it cannot reopen by itself later.
            if (spot != null && shown?.spot == null) openSpot.value = null
            if (outing != null && shown?.focus == null) focus.value = null
            setState { mapped }
        }.launchIn(viewModelScope)
    }

    override suspend fun handle(intent: MapIntent) {
        when (intent) {
            is MapIntent.CatsTapped -> {
                val cats = intent.ids.toSet()
                when (cats.size) {
                    0 -> Unit
                    1 -> emit(MapEffect.OpenCat(cats.single()))
                    else -> openSpot.value = cats
                }
            }
            MapIntent.SpotDismissed -> openSpot.value = null
            is MapIntent.OutingFocused -> {
                openSpot.value = null
                focus.value = intent.encounterId
            }
            MapIntent.FocusCleared -> focus.value = null
        }
    }
}
