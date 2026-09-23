package dev.catsradar.app.photo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class CameraRequest {

    var isPending by mutableStateOf(false)
        private set

    fun post() {
        isPending = true
    }

    /** True at most once per pending request, however many times it was posted. */
    fun consume(): Boolean = isPending.also { isPending = false }
}
