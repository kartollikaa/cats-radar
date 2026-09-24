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
    data class CoatPromptPicked(val coat: CoatOption) : CounterIntent
    data object CoatPromptDismissed : CounterIntent
    data class LocationPermissionResult(val granted: Boolean) : CounterIntent
    data object GrantLocationClicked : CounterIntent
    data object LocationPermissionHintDismissed : CounterIntent
}
