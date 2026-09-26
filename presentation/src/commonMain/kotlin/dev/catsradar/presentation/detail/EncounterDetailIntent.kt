package dev.catsradar.presentation.detail

import dev.catsradar.presentation.coat.CoatOption

sealed interface EncounterDetailIntent {
    data object BackClicked : EncounterDetailIntent
    data object DeleteClicked : EncounterDetailIntent
    data object UndoClicked : EncounterDetailIntent

    /** [coat] of null clears it. */
    data class CoatPicked(val catId: String, val coat: CoatOption?) : EncounterDetailIntent

    data class TakePhotoClicked(val catId: String) : EncounterDetailIntent
    data class PickPhotoClicked(val catId: String) : EncounterDetailIntent
    data class PhotoClicked(val catId: String, val photoId: String) : EncounterDetailIntent
    data class CoordinatesClicked(val catId: String) : EncounterDetailIntent
    data class SetLocationClicked(val catId: String) : EncounterDetailIntent

    /** [uri] is null when the camera was cancelled. */
    data class PhotoTaken(val catId: String, val uri: String?) : EncounterDetailIntent

    /** In the order picked; empty when the picker was dismissed. */
    data class PhotosPicked(val catId: String, val uris: List<String>) : EncounterDetailIntent
}
