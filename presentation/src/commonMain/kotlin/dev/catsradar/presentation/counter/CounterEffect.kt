package dev.catsradar.presentation.counter

import kotlinx.collections.immutable.ImmutableList

sealed interface CounterEffect {
    data object HapticTick : CounterEffect
    data class AttachLocation(val encounterId: String) : CounterEffect
    data class CancelLocationAttach(val encounterId: String) : CounterEffect
    data object RequestLocationPermission : CounterEffect

    data object OpenCamera : CounterEffect
    data object PickPhotos : CounterEffect
    data class StartImport(val uris: ImmutableList<String>) : CounterEffect
    data object PhotoNotSaved : CounterEffect

    /** Saving the cats counted on a photo failed after its sheet had closed. */
    data object CatsNotSaved : CounterEffect

    data class MilestoneReached(val value: Int) : CounterEffect

    /** Ending a walk takes a held press, and the last one was let go too soon. */
    data object WalkNeedsHold : CounterEffect

    /** The original at [uri] has been copied and is no longer needed. */
    data class DiscardCapture(val uri: String) : CounterEffect
}
