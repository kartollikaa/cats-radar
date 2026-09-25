package dev.catsradar.presentation.detail

sealed interface EncounterDetailEffect {
    data object NavigateBack : EncounterDetailEffect
    data object OpenCamera : EncounterDetailEffect
    data object OpenPhotoPicker : EncounterDetailEffect
    data class OpenPhoto(val photoId: String) : EncounterDetailEffect
    data object OpenMap : EncounterDetailEffect
    data object OpenLocationPicker : EncounterDetailEffect
    data object PhotoNotAttached : EncounterDetailEffect
    data object PhotoAlreadyThere : EncounterDetailEffect

    /** The camera's original at [uri] is no longer needed. */
    data class DiscardCapture(val uri: String) : EncounterDetailEffect
}
