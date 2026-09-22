package dev.catsradar.presentation.counter

import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.statistics.RateState

data class CounterState(
    val totalLabel: String,
    val undoVisible: Boolean,
    val locationPermissionHintVisible: Boolean = false,
    /** Null when no outing is in progress — the last cat was long enough ago to have closed it. */
    val currentOuting: CurrentOutingState? = null,
    /** How many cats the current run of taps has added; null once the burst has faded. */
    val tapBurst: Int? = null,
    /** The coat of the cat the undo window belongs to, so the grid can show which one it was. */
    val lastCoat: CoatOption? = null,
    /** Whether the walking notification is up, so the Counter and Settings never disagree. */
    val walkingMode: Boolean = false,
    /** Null unless an import is running. */
    val importProgress: ImportProgressState? = null,
    /** Null until an import finishes, and again once it is dismissed. */
    val importSummary: ImportSummaryState? = null,
)

data class ImportProgressState(val done: Int, val total: Int)

/**
 * [skipped] and [failed] are null when there were none — a run where everything worked should not
 * read as a report card. [undoable] is false once the added cats have been undone, or when none
 * were added.
 */
data class ImportSummaryState(
    val added: Int,
    val skipped: Int?,
    val failed: Int?,
    val undoable: Boolean,
)

/** [rate] is null until the outing is long enough and busy enough to measure. */
data class CurrentOutingState(
    /** The count itself, not a label: only the platform knows the plural form for it. */
    val count: Int,
    val elapsedLabel: String,
    val rate: RateState?,
)
