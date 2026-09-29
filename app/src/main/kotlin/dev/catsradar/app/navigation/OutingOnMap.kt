package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** A map above the screen it was opened from, showing the outing of the cat [encounterId] alone. */
@Serializable
data class OutingOnMap(val encounterId: String) : NavKey
