package dev.catsradar.presentation.encounters

sealed interface EncountersIntent {
    data class RowClicked(val id: String) : EncountersIntent

    data class RowLongPressed(val id: String) : EncountersIntent

    data object SelectionDismissed : EncountersIntent

    data object DeleteSelectedClicked : EncountersIntent

    data object UndoClicked : EncountersIntent
}
