package dev.catsradar.presentation.detail

sealed interface EncounterDetailEffect {
    data object NavigateBack : EncounterDetailEffect
    data object OpenCamera : EncounterDetailEffect
    data object OpenPhotoPicker : EncounterDetailEffect
    data object PhotoNotAttached : EncounterDetailEffect

    /** The camera's original at [uri] is no longer needed. */
    data class DiscardCapture(val uri: String) : EncounterDetailEffect
}
