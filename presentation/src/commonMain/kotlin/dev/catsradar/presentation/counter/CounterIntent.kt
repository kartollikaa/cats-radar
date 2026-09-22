package dev.catsradar.presentation.counter

sealed interface CounterIntent {
    data object TallyClicked : CounterIntent
    data object CameraClicked : CounterIntent

    /** [uri] is null when the camera was cancelled or produced nothing. */
    data class PhotoCaptured(val uri: String?) : CounterIntent
    data object UndoClicked : CounterIntent
    data class LocationPermissionResult(val granted: Boolean) : CounterIntent
    data object GrantLocationClicked : CounterIntent
    data object LocationPermissionHintDismissed : CounterIntent
}
