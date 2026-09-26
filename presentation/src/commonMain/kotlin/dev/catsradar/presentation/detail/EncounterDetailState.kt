package dev.catsradar.presentation.detail

import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.LocationLabel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

sealed interface EncounterDetailState {

    data object Loading : EncounterDetailState

    data class Loaded(
        val dayLabel: String,
        val timeLabel: String,
        val location: LocationLabel,
        val coordinatesLabel: String?,
        val accuracyMeters: Int?,
        /** Oldest first. */
        val photos: ImmutableList<DetailPhoto> = persistentListOf(),
        val coat: CoatOption? = null,
        val addPhoto: AddPhoto = AddPhoto.READY,
        /** Null unless several photos are being attached. */
        val attachProgress: AttachProgress? = null,
        val onTheMap: Boolean = false,
        /** Whether the cat can be given a location on a map: it has none. */
        val setsLocation: Boolean = false,
        /** Null while the cat has no named place: no location, or its cell not named yet. */
        val place: DetailPlace? = null,
    ) : EncounterDetailState

    /** The user deleted this encounter from this screen; [undoVisible] is false once the window closed. */
    data class Deleted(val undoVisible: Boolean) : EncounterDetailState

    /** No live encounter has this id: it was never there, was purged, or was deleted elsewhere. */
    data object Missing : EncounterDetailState
}

/** [path] is the absolute path of the app's full copy. */
data class DetailPhoto(val id: String, val path: String)

enum class AddPhoto { READY, ATTACHING }

/** [done] of the [total] photos being attached are through, whether or not each one landed. */
data class AttachProgress(val done: Int, val total: Int) {
    val fraction: Float get() = done.toFloat() / total
}

/** [title] is the city, or the country when no city is known; [country] is set only under a city. */
data class DetailPlace(val title: String, val country: String?, val flag: String?)
