package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class LocationPicker(val encounterId: String) : NavKey
