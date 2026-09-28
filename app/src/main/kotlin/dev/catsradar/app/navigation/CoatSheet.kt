package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The coat sheet for the cat [catId], over the detail. */
@Serializable
data class CoatSheet(val catId: String) : NavKey
