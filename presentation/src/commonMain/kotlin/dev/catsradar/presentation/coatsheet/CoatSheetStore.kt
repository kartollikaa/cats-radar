package dev.catsradar.presentation.coatsheet

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.presentation.Store
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.coat.toCatCoat
import dev.catsradar.presentation.runStorageWrite
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

sealed interface CoatSheetIntent {
    data class CoatClicked(val coat: CoatOption) : CoatSheetIntent

    data object NoCoatClicked : CoatSheetIntent
}

sealed interface CoatSheetEffect {
    data object Close : CoatSheetEffect
}

class CoatSheetStore(
    private val catId: String,
    observeEncounter: ObserveEncounter,
    private val setCoat: SetCoat,
    stateMapper: CoatSheetStateMapper,
) : Store<CoatSheetState, CoatSheetIntent, CoatSheetEffect>(CoatSheetState.Loading) {

    private var answered = false

    init {
        observeEncounter(catId)
            .onEach { cat -> if (cat == null) emit(CoatSheetEffect.Close) else setState { stateMapper.map(cat.coat) } }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: CoatSheetIntent) {
        // Set before the write suspends: a second tap in flight must see it and write nothing.
        if (answered) return
        answered = true
        val coat = when (intent) {
            is CoatSheetIntent.CoatClicked -> intent.coat.toCatCoat()
            CoatSheetIntent.NoCoatClicked -> null
        }
        runStorageWrite { setCoat(catId, coat) }
        emit(CoatSheetEffect.Close)
    }
}
