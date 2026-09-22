package dev.catsradar.presentation.counter

sealed interface CounterEffect {
    data object HapticTick : CounterEffect
    data class AttachLocation(val encounterId: String) : CounterEffect
    data class CancelLocationAttach(val encounterId: String) : CounterEffect
    data object RequestLocationPermission : CounterEffect

    data object OpenCamera : CounterEffect
    data object PhotoNotSaved : CounterEffect
}
