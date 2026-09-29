package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** A map of its own, apart from the Map tab's, showing every cat with the view on the cat [encounterId]. */
@Serializable
data class CatOnMap(val encounterId: String) : NavKey
