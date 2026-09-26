package dev.catsradar.presentation.locationpicker

import dev.catsradar.presentation.map.MapArea

sealed interface LocationPickerState {

    data object Loading : LocationPickerState

    data class Picking(
        /** Where the map opens; null is the whole world. */
        val start: MapArea?,
        val locating: Boolean = false,
        val saving: Boolean = false,
        /** Where the phone was found, until the map reports it has moved there. */
        val moveTo: MapArea? = null,
    ) : LocationPickerState
}
