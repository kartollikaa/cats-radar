package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import dev.catsradar.presentation.coat.CoatOption
import kotlinx.serialization.Serializable

/**
 * One spot's cats, listed over the map under the [coats] the map showed. The choice is made on the
 * map, beneath the list and anything opened from it, so it cannot change while the list is up.
 */
@Serializable
data class MapSpot(val catIds: Set<String>, val coats: Set<CoatOption?>) : NavKey
