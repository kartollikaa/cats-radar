package dev.catsradar.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** An outing another tab asked the map to show, by the id of one of its cats. */
class MapFocusRequest {

    var outing by mutableStateOf<String?>(null)
        private set

    fun post(encounterId: String) {
        outing = encounterId
    }

    fun consume(): String? = outing.also { outing = null }
}
