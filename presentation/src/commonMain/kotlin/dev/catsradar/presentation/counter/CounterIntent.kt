package dev.catsradar.presentation.counter

sealed interface CounterIntent {
    data object TallyClicked : CounterIntent
    data object UndoClicked : CounterIntent
}
