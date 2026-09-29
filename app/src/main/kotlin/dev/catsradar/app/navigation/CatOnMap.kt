package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** A map above the screen it was opened from, showing every cat with the view on the cat [encounterId]. */
@Serializable
data class CatOnMap(val encounterId: String) : NavKey
