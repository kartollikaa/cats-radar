package dev.catsradar.presentation.detail

import dev.catsradar.presentation.coat.CoatOption

sealed interface EncounterDetailIntent {
    data object DeleteClicked : EncounterDetailIntent
    data object UndoClicked : EncounterDetailIntent

    /** [coat] of null clears it. */
    data class CoatPicked(val coat: CoatOption?) : EncounterDetailIntent

    data object TakePhotoClicked : EncounterDetailIntent
    data object PickPhotoClicked : EncounterDetailIntent

    /** [uri] is null when the camera was cancelled. */
    data class PhotoTaken(val uri: String?) : EncounterDetailIntent

    /** [uri] is null when the picker was dismissed. */
    data class PhotoPicked(val uri: String?) : EncounterDetailIntent
}
