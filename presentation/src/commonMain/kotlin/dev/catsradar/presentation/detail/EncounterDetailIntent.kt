package dev.catsradar.presentation.detail

import dev.catsradar.presentation.coat.CoatOption

sealed interface EncounterDetailIntent {
    data object BackClicked : EncounterDetailIntent
    data object DeleteClicked : EncounterDetailIntent
    data object UndoClicked : EncounterDetailIntent

    /** [coat] of null clears it. */
    data class CoatPicked(val coat: CoatOption?) : EncounterDetailIntent

    data object TakePhotoClicked : EncounterDetailIntent
    data object PickPhotoClicked : EncounterDetailIntent
    data class PhotoClicked(val photoId: String) : EncounterDetailIntent
    data object CoordinatesClicked : EncounterDetailIntent

    /** [uri] is null when the camera was cancelled. */
    data class PhotoTaken(val uri: String?) : EncounterDetailIntent

    /** In the order picked; empty when the picker was dismissed. */
    data class PhotosPicked(val uris: List<String>) : EncounterDetailIntent
}
