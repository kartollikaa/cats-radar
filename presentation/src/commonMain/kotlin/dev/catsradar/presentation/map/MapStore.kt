package dev.catsradar.presentation.map

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.coat.CoatOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

sealed interface MapIntent {
    data class CatsTapped(val ids: List<String>) : MapIntent

    data object SpotDismissed : MapIntent

    data class OutingFocused(val encounterId: String) : MapIntent

    data object FocusCleared : MapIntent

    /** Shows cats of [coat], or stops showing them; null stands for a cat with no coat noted. */
    data class CoatToggled(val coat: CoatOption?) : MapIntent

    data object CoatFilterCleared : MapIntent

    data object HeatToggled : MapIntent
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

    private val choices = MutableStateFlow(MapChoices())

    init {
        combine(observeEncounters(), choices) { encounters, chosen ->
            val mapped = stateMapper.map(encounters, clock.today(timeZone), chosen)
            val shown = mapped as? MapState.Located
            // A spot or focus the cats no longer match is let go, so it cannot reopen by itself later.
            if (chosen.spot != null && shown?.spot == null) choices.update { it.copy(spot = null) }
            if (chosen.focus != null && shown?.focus == null) choices.update { it.copy(focus = null) }
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
                    else -> choices.update { it.copy(spot = cats) }
                }
            }
            MapIntent.SpotDismissed -> choices.update { it.copy(spot = null) }
            is MapIntent.OutingFocused -> choices.update { it.copy(spot = null, focus = intent.encounterId) }
            MapIntent.FocusCleared -> choices.update { it.copy(focus = null) }
            is MapIntent.CoatToggled -> choices.update { chosen ->
                val coats = if (intent.coat in chosen.coats) chosen.coats - intent.coat else chosen.coats + intent.coat
                chosen.copy(coats = coats)
            }
            MapIntent.CoatFilterCleared -> choices.update { it.copy(coats = emptySet()) }
            MapIntent.HeatToggled -> choices.update { it.copy(heat = !it.heat) }
        }
    }
}
