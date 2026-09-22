package dev.catsradar.presentation.counter

sealed interface CounterEffect {
    data object HapticTick : CounterEffect
}
