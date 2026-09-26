package dev.catsradar.presentation.detail

sealed interface EncounterDetailEffect {
    data object NavigateBack : EncounterDetailEffect
    data class OpenCamera(val catId: String) : EncounterDetailEffect
    data class OpenPhotoPicker(val catId: String) : EncounterDetailEffect
    data class OpenPhoto(val catId: String, val photoId: String) : EncounterDetailEffect
    data class OpenMap(val catId: String) : EncounterDetailEffect
    data class OpenLocationPicker(val catId: String) : EncounterDetailEffect
    data object PhotoNotAttached : EncounterDetailEffect

    /** More than one photo of a pick was not attached. */
    data class PhotosNotAttached(val count: Int) : EncounterDetailEffect
    data object PhotoAlreadyThere : EncounterDetailEffect

    /** Every photo of a pick of several is already on the cat. */
    data object PhotosAlreadyThere : EncounterDetailEffect

    /** The camera's original at [uri] is no longer needed. */
    data class DiscardCapture(val uri: String) : EncounterDetailEffect
}
