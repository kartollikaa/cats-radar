package dev.catsradar.presentation.map

import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.EncounterListItem
import kotlinx.collections.immutable.ImmutableList

sealed interface MapState {
    data object Loading : MapState

    /** No cat has a location yet. */
    data object Empty : MapState

    data class Located(
        val points: ImmutableList<MapPoint>,
        val area: MapArea,
        /** The cats of one spot the user opened, while it is open. */
        val spot: MapSpot? = null,
        val focus: MapFocus? = null,
    ) : MapState
}

data class MapPoint(val id: String, val latitude: Double, val longitude: Double, val coat: CoatOption?)

/** The part of the world the map opens on, in degrees. */
data class MapArea(val south: Double, val west: Double, val north: Double, val east: Double)

/** An outing shown alone; its located cats are the points, in the order they were seen. */
data class MapFocus(val outingId: String, val label: String)

data class MapSpot(val catCount: Int, val rows: ImmutableList<EncounterListItem>)
