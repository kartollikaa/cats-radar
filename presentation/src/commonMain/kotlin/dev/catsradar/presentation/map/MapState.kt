package dev.catsradar.presentation.map

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.ImmutableList

sealed interface MapState {
    data object Loading : MapState

    /** No cat has a location yet. */
    data object Empty : MapState

    data class Located(val points: ImmutableList<MapPoint>, val area: MapArea) : MapState
}

data class MapPoint(val id: String, val latitude: Double, val longitude: Double, val coat: CoatOption?)

/** The part of the world the map opens on, in degrees. */
data class MapArea(val south: Double, val west: Double, val north: Double, val east: Double)
