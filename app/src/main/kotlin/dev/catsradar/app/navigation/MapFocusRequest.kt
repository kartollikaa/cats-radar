package dev.catsradar.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.catsradar.presentation.map.MapIntent

/** An outing or a single cat another screen asked the map to show, each by the id of a cat. */
class MapFocusRequest {

    var pending by mutableStateOf<MapIntent?>(null)
        private set

    fun postOuting(encounterId: String) {
        pending = MapIntent.OutingFocused(encounterId)
    }

    fun postCat(encounterId: String) {
        pending = MapIntent.CatRequested(encounterId)
    }

    fun consume(): MapIntent? = pending.also { pending = null }
}
