package dev.catsradar.presentation.map

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

sealed interface MapState {
    data object Loading : MapState

    /** No cat has a location yet. */
    data object Empty : MapState

    data class Located(
        val points: ImmutableList<MapPoint>,
        val area: MapArea,
        val focus: MapFocus? = null,
        val heat: Boolean = false,
        /** The coats shown, null standing for a cat with none noted; empty shows every cat. */
        val shownCoats: ImmutableSet<CoatOption?> = persistentSetOf(),
        val coatFilterActive: Boolean = false,
        val filterMatchesNone: Boolean = false,
    ) : MapState
}

data class MapPoint(val id: String, val latitude: Double, val longitude: Double, val coat: CoatOption?)

/** The part of the world the map opens on, in degrees. */
data class MapArea(val south: Double, val west: Double, val north: Double, val east: Double)

/** An outing shown alone, with the [lines] drawn for it — see [MapStateMapper] for which route that is. */
data class MapFocus(val outingId: String, val label: String, val lines: ImmutableList<MapLine>)

data class MapLine(val positions: ImmutableList<MapPosition>)

data class MapPosition(val latitude: Double, val longitude: Double)
