package dev.catsradar.presentation.detail

sealed interface EncounterDetailIntent {
    data object BackClicked : EncounterDetailIntent
    data object DeleteClicked : EncounterDetailIntent
    data object UndoClicked : EncounterDetailIntent

    /** Which cat the pages show: where a swipe came to rest, a cat tapped on the photo on screen, or one asked for. */
    sealed interface ShowCat : EncounterDetailIntent {
        val catId: String
    }

    /** The pages came to rest on [catId]'s page. */
    data class PageSettled(override val catId: String) : ShowCat

    data class PhotoCatClicked(override val catId: String) : ShowCat

    /** Another screen asked for [catId] while this one was open under it. */
    data class CatRequested(override val catId: String) : ShowCat

    data class TakePhotoClicked(val catId: String) : EncounterDetailIntent
    data class PickPhotoClicked(val catId: String) : EncounterDetailIntent
    data class PhotoClicked(val catId: String, val photoId: String) : EncounterDetailIntent
    data class CoordinatesClicked(val catId: String) : EncounterDetailIntent

    data class CoatCardClicked(val catId: String) : EncounterDetailIntent
    data class SetLocationClicked(val catId: String) : EncounterDetailIntent

    /** [uri] is null when the camera was cancelled. */
    data class PhotoTaken(val catId: String, val uri: String?) : EncounterDetailIntent

    /** In the order picked; empty when the picker was dismissed. */
    data class PhotosPicked(val catId: String, val uris: List<String>) : EncounterDetailIntent
}
