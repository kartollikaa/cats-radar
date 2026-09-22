package dev.catsradar.presentation.counter

import kotlinx.collections.immutable.ImmutableList

sealed interface CounterEffect {
    data object HapticTick : CounterEffect
    data class AttachLocation(val encounterId: String) : CounterEffect
    data class CancelLocationAttach(val encounterId: String) : CounterEffect
    data object RequestLocationPermission : CounterEffect

    /** The notification itself belongs to the platform layer; the store only says when. */
    data class WalkingMode(val enabled: Boolean) : CounterEffect

    data object OpenCamera : CounterEffect
    data object PickPhotos : CounterEffect
    data class StartImport(val uris: ImmutableList<String>) : CounterEffect
    data object PhotoNotSaved : CounterEffect

    data class MilestoneReached(val value: Int) : CounterEffect

    /** The original at [uri] has been copied and is no longer needed. */
    data class DiscardCapture(val uri: String) : CounterEffect
}
