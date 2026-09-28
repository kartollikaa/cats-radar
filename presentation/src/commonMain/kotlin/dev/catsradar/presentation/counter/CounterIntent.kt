package dev.catsradar.presentation.counter

import dev.catsradar.presentation.coat.CoatOption
import kotlinx.collections.immutable.ImmutableList

sealed interface CounterIntent {
    data object TallyClicked : CounterIntent
    data object CameraClicked : CounterIntent

    /** [uri] is null when the camera was cancelled or produced nothing. */
    data class PhotoCaptured(val uri: String?) : CounterIntent
    data object UndoClicked : CounterIntent
    data class WalkingModeToggled(val enabled: Boolean) : CounterIntent

    /** A press on the walk's stop let go before the hold was up. */
    data object WalkHoldReleased : CounterIntent

    /** Importing from the gallery: a sub-flow of the Counter, not a screen of its own. */
    sealed interface Import : CounterIntent {
        data object Requested : Import

        /** [uris] is empty when the picker was dismissed without choosing anything. */
        data class PhotosPicked(val uris: ImmutableList<String>) : Import
        data class Progressed(val done: Int, val total: Int) : Import

        /** The same run may be reported again; [runId] tells a repeat from a new run. */
        data class Finished(
            val runId: String,
            val addedIds: ImmutableList<String>,
            val skipped: Int,
            val failed: Int,
        ) : Import
        data object UndoClicked : Import
        data object SummaryDismissed : Import
    }

    /** Logs a cat of this coat straight away — the fast path for a coat you can see. */
    data class CoatTallyClicked(val coat: CoatOption) : CounterIntent

    /** Answering the coat question after a photo: a sub-flow of the Counter, like an import. */
    sealed interface CoatPrompt : CounterIntent {
        data class Picked(val coat: CoatOption) : CoatPrompt

        /** The paw: a cat on the photo whose coat nobody saw. */
        data object UnseenPicked : CoatPrompt
        data object SeveralClicked : CoatPrompt
        data object OneCatClicked : CoatPrompt

        /** [coat] is the tapped cat's, so a tap landing after the tray moved takes out no other cat. */
        data class TrayCatClicked(val index: Int, val coat: CoatOption?) : CoatPrompt

        data object SaveClicked : CoatPrompt
        data object Dismissed : CoatPrompt
    }

    data class LocationPermissionResult(val granted: Boolean) : CounterIntent
    data object GrantLocationClicked : CounterIntent
    data object LocationPermissionHintDismissed : CounterIntent
}
