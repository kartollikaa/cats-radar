package dev.catsradar.presentation.detail

sealed interface EncounterDetailEffect {
    data object NavigateBack : EncounterDetailEffect
}
