package dev.catsradar.presentation.counter

import dev.catsradar.presentation.coat.CoatOption

sealed interface CounterIntent {
    data object TallyClicked : CounterIntent
    data object CameraClicked : CounterIntent

    /** [uri] is null when the camera was cancelled or produced nothing. */
    data class PhotoCaptured(val uri: String?) : CounterIntent
    data object UndoClicked : CounterIntent

    /** Logs a cat of this coat straight away — the fast path for a coat you can see. */
    data class CoatTallyClicked(val coat: CoatOption) : CounterIntent
    data class LocationPermissionResult(val granted: Boolean) : CounterIntent
    data object GrantLocationClicked : CounterIntent
    data object LocationPermissionHintDismissed : CounterIntent
}
