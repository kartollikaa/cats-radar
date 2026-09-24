package dev.catsradar.presentation.map

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.ObserveWalkTracks
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.coat.CoatOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

sealed interface MapIntent {
    data class CatsTapped(val ids: List<String>) : MapIntent

    data class OutingFocused(val encounterId: String) : MapIntent

    data object FocusCleared : MapIntent

    /** Shows cats of [coat], or stops showing them; null stands for a cat with no coat noted. */
    data class CoatToggled(val coat: CoatOption?) : MapIntent

    data object CoatFilterCleared : MapIntent

    data object HeatToggled : MapIntent
}

sealed interface MapEffect {
    data class OpenCat(val id: String) : MapEffect

    /** Lists [catIds], showing only cats of [coats] as the map does. */
    data class OpenSpot(val catIds: Set<String>, val coats: Set<CoatOption?>) : MapEffect
}

class MapStore(
    observeEncounters: ObserveEncounters,
    observeWalkTracks: ObserveWalkTracks,
    private val stateMapper: MapStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<MapState, MapIntent, MapEffect>(MapState.Loading) {

    private val choices = MutableStateFlow(MapChoices())

    init {
        // A route is only ever drawn for a focused outing, so nothing subscribes to walk tracks until one is chosen.
        val walks = choices.map { it.focus != null }.distinctUntilChanged()
            .flatMapLatest { focused -> if (focused) observeWalkTracks() else flowOf(emptyList()) }
        combine(observeEncounters(), walks, choices) { encounters, tracks, chosen ->
            val mapped = stateMapper.map(encounters, clock.today(timeZone), chosen, tracks)
            val shown = mapped as? MapState.Located
            // A focus the cats no longer match is let go, so it cannot reopen by itself later.
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
                    else -> emit(MapEffect.OpenSpot(cats, choices.value.coats))
                }
            }
            is MapIntent.OutingFocused -> choices.update { it.copy(focus = intent.encounterId) }
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
