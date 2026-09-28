package dev.catsradar.presentation.counter

import dev.catsradar.domain.Tuning
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.statistics.MilestoneState
import dev.catsradar.presentation.statistics.RateState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap

data class CounterState(
    val totalLabel: String,
    /** The number [totalLabel] shows, unformatted; null until the total has been read. */
    val count: Int?,
    val undoVisible: Boolean,
    val locationPermissionHintVisible: Boolean = false,
    /** Null when no outing is in progress — the last cat was long enough ago to have closed it. */
    val currentOuting: CurrentOutingState? = null,
    /** How many cats the open run of taps holds, taps still being written included; null when it holds none. */
    val tapBurst: Int? = null,
    /** The coat of the cat the undo window belongs to, so the grid can show which one it was. */
    val lastCoat: CoatOption? = null,
    val walkingMode: Boolean = false,
    /** Null unless an import is running. */
    val importProgress: ImportProgressState? = null,
    /** Null until an import finishes, and again once it is dismissed. */
    val importSummary: ImportSummaryState? = null,
    /** Null unless a photo just taken is waiting for its coat. */
    val coatPrompt: CoatPromptState? = null,
    /** Null before the total is read, and past the last milestone. */
    val milestone: CounterMilestoneState? = null,
    /**
     * The rung the count has just landed on, until the run of taps that reached it closes. The ring shows it in
     * place of [milestone] and [currentOuting], which stay so the number keeps the room they give it.
     */
    val milestoneMoment: MilestoneMomentState? = null,
)

data class MilestoneMomentState(val value: Int)

/** [fraction] is how far the total has come from the milestone already reached toward [next]. */
data class CounterMilestoneState(val next: MilestoneState, val fraction: Float)

/**
 * The question about [catId]'s photo, [photoId] (null when the cat has none). [thumbPath] is absolute; null when no
 * thumbnail could be made from the photo. Null [counting] asks for one coat.
 */
data class CoatPromptState(
    val catId: String,
    val photoId: String?,
    val thumbPath: String?,
    val counting: CoatCountState? = null,
)

/** The cats counted on the photo, in the order their coats were tapped; a null is a cat whose coat nobody saw. */
data class CoatCountState(val tray: ImmutableList<CoatOption?> = persistentListOf()) {
    /** How many cats of each coat [tray] holds; a coat it holds none of is absent. */
    val counts: ImmutableMap<CoatOption?, Int> get() = tray.groupingBy { it }.eachCount().toImmutableMap()

    val canAdd: Boolean get() = tray.size < Tuning.SHOT_MAX_CATS

    /** Null until a cat is counted. */
    val catCount: Int? get() = tray.size.takeIf { it > 0 }
}

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
    /** Absolute; the first added cats' thumbnails, in the run's order, only those that have one. */
    val thumbPaths: ImmutableList<String> = persistentListOf(),
)

/** [rate] is null until the outing is long enough and busy enough to measure. */
data class CurrentOutingState(
    /** The count itself, not a label: only the platform knows the plural form for it. */
    val count: Int,
    val elapsedLabel: String,
    val rate: RateState?,
)
