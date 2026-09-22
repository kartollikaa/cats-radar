package dev.catsradar.presentation.detail

sealed interface EncounterDetailIntent {
    data object DeleteClicked : EncounterDetailIntent
    data object UndoClicked : EncounterDetailIntent
}
