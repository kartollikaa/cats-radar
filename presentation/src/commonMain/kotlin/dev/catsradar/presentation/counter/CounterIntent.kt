package dev.catsradar.presentation.counter

sealed interface CounterIntent {
    data object TallyClicked : CounterIntent
    data object UndoClicked : CounterIntent
    data class LocationPermissionResult(val granted: Boolean) : CounterIntent
    data object GrantLocationClicked : CounterIntent
    data object LocationPermissionHintDismissed : CounterIntent
}
