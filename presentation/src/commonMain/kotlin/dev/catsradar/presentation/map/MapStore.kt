package dev.catsradar.presentation.map

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.ObserveOutingTracks
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.coat.CoatOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

sealed interface MapIntent {
    data class CatsTapped(val ids: List<String>) : MapIntent

    data class OutingFocused(val encounterId: String) : MapIntent

    data object FocusCleared : MapIntent

    /** Shows every cat, with the view on this one. */
    data class CatRequested(val encounterId: String) : MapIntent

    /** The view is done moving onto the requested cat. */
    data object CatReached : MapIntent

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
    observeOutingTracks: ObserveOutingTracks,
    private val stateMapper: MapStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<MapState, MapIntent, MapEffect>(MapState.Loading) {

    private val choices = MutableStateFlow(MapChoices())

    init {
        val encounters = observeEncounters().shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)
        // A fresh combine per focus id: a pairing can only carry that focus's own walks answer, never the
        // unfocused branch's empty list or one still pending from a focus switch in flight.
        val chosenWithWalks = choices.distinctUntilChangedBy { it.focus }.flatMapLatest { chosen ->
            val walks = chosen.focus?.let { observeOutingTracks(encounters, it) } ?: flowOf(emptyList())
            combine(walks, choices) { tracks, latest -> latest to tracks }
        }
        combine(encounters, chosenWithWalks) { cats, (chosen, tracks) ->
            val mapped = stateMapper.map(cats, clock.today(timeZone), chosen, tracks)
            val shown = mapped as? MapState.Located
            // A focus or a cat the cats no longer match is let go, so it cannot come back by itself later — but
            // only while it is still the live choice: a pairing catching up to a newer one must not clear that.
            if (chosen.focus != null && shown?.focus == null) {
                choices.update { if (it.focus == chosen.focus) it.copy(focus = null) else it }
            }
            if (chosen.cat != null && shown?.catArea == null) {
                choices.update { if (it.cat == chosen.cat) it.copy(cat = null) else it }
            }
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
            is MapIntent.CatRequested -> choices.value = MapChoices(cat = intent.encounterId)
            MapIntent.CatReached -> choices.update { it.copy(cat = null) }
        }
    }
}
