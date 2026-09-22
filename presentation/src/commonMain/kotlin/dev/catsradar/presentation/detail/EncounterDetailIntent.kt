package dev.catsradar.presentation.detail

import dev.catsradar.presentation.coat.CoatOption

sealed interface EncounterDetailIntent {
    data object DeleteClicked : EncounterDetailIntent
    data object UndoClicked : EncounterDetailIntent

    /** [coat] of null clears it. */
    data class CoatPicked(val coat: CoatOption?) : EncounterDetailIntent
}
