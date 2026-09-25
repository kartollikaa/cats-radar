package dev.catsradar.presentation.detail

import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.LocationLabel

sealed interface EncounterDetailState {

    data object Loading : EncounterDetailState

    data class Loaded(
        val dayLabel: String,
        val timeLabel: String,
        val location: LocationLabel,
        val coordinatesLabel: String?,
        val accuracyMeters: Int?,
        /** Absolute path of the app's copy, or null when the cat has no photo. */
        val photoPath: String? = null,
        val coat: CoatOption? = null,
        /** Null when the cat has a photo of its own, which is never replaced. */
        val addPhoto: AddPhoto? = null,
        val onTheMap: Boolean = false,
        /** Null while the cat has no named place: no location, or its cell not named yet. */
        val place: DetailPlace? = null,
    ) : EncounterDetailState

    /** The user deleted this encounter from this screen; [undoVisible] is false once the window closed. */
    data class Deleted(val undoVisible: Boolean) : EncounterDetailState

    /** No live encounter has this id: it was never there, was purged, or was deleted elsewhere. */
    data object Missing : EncounterDetailState
}

enum class AddPhoto { READY, ATTACHING }

/** [title] is the city, or the country when no city is known; [country] is set only under a city. */
data class DetailPlace(val title: String, val country: String?, val flag: String?)
