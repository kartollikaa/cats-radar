package dev.catsradar.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.catsradar.presentation.map.MapIntent

/** An outing a spot's list asked the map beneath it to show, by the id of one of its cats. */
class MapFocusRequest {

    var pending by mutableStateOf<MapIntent?>(null)
        private set

    fun postOuting(encounterId: String) {
        pending = MapIntent.OutingFocused(encounterId)
    }

    fun consume(): MapIntent? = pending.also { pending = null }
}
