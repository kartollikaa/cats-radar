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
        /** Absolute path of the app's copy, or null when this cat was a tally. */
        val photoPath: String? = null,
        val coat: CoatOption? = null,
        /** Null when the cat has a photo of its own, which is never replaced. */
        val addPhoto: AddPhoto? = null,
    ) : EncounterDetailState

    /** The user deleted this encounter from this screen; [undoVisible] is false once the window closed. */
    data class Deleted(val undoVisible: Boolean) : EncounterDetailState

    /** No live encounter has this id: it was never there, was purged, or was deleted elsewhere. */
    data object Missing : EncounterDetailState
}

enum class AddPhoto { READY, ATTACHING }
