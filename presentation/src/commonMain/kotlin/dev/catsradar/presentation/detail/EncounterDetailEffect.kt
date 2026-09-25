package dev.catsradar.presentation.detail

sealed interface EncounterDetailEffect {
    data object NavigateBack : EncounterDetailEffect
    data class OpenCamera(val catId: String) : EncounterDetailEffect
    data class OpenPhotoPicker(val catId: String) : EncounterDetailEffect
    data class OpenPhoto(val catId: String, val photoId: String) : EncounterDetailEffect
    data class OpenMap(val catId: String) : EncounterDetailEffect
    data object PhotoNotAttached : EncounterDetailEffect
    data object PhotoAlreadyThere : EncounterDetailEffect

    /** The camera's original at [uri] is no longer needed. */
    data class DiscardCapture(val uri: String) : EncounterDetailEffect
}
