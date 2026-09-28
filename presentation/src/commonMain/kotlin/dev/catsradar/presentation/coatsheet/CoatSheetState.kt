package dev.catsradar.presentation.coatsheet

import dev.catsradar.presentation.coat.CoatOption

sealed interface CoatSheetState {
    data object Loading : CoatSheetState

    /** [coat] of null: no coat noted. */
    data class Open(val coat: CoatOption?, val hint: CoatSheetHint) : CoatSheetState
}

enum class CoatSheetHint { PICK_ANOTHER, TAP_ONE }
