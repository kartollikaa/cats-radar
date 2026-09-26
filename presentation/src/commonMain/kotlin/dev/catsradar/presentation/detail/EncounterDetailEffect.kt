package dev.catsradar.presentation.detail

sealed interface EncounterDetailEffect {
    data object NavigateBack : EncounterDetailEffect
    data object OpenCamera : EncounterDetailEffect
    data object OpenPhotoPicker : EncounterDetailEffect
    data class OpenPhoto(val photoId: String) : EncounterDetailEffect
    data object OpenMap : EncounterDetailEffect
    data object PhotoNotAttached : EncounterDetailEffect

    /** More than one photo of a pick was not attached. */
    data class PhotosNotAttached(val count: Int) : EncounterDetailEffect
    data object PhotoAlreadyThere : EncounterDetailEffect

    /** Every photo of a pick of several is already on the cat. */
    data object PhotosAlreadyThere : EncounterDetailEffect

    /** The camera's original at [uri] is no longer needed. */
    data class DiscardCapture(val uri: String) : EncounterDetailEffect
}
