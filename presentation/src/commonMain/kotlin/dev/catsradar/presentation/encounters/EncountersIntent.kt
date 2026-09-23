package dev.catsradar.presentation.encounters

sealed interface EncountersIntent {
    data class EncounterClicked(val id: String) : EncountersIntent

    data class EncounterLongPressed(val id: String) : EncountersIntent

    data object SelectionDismissed : EncountersIntent

    data object DeleteSelectedClicked : EncountersIntent

    data object UndoClicked : EncountersIntent
}
