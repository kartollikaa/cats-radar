package dev.catsradar.presentation.map

import dev.catsradar.presentation.encounters.EncountersRow
import kotlinx.collections.immutable.ImmutableList

sealed interface MapSpotState {
    data object Loading : MapSpotState

    data class Listed(val catCount: Int, val rows: ImmutableList<EncountersRow>) : MapSpotState
}
