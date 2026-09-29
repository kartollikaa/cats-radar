package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** A map of its own, apart from the Map tab's, showing the outing of the cat [encounterId] alone. */
@Serializable
data class OutingOnMap(val encounterId: String) : NavKey
