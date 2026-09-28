package dev.catsradar.presentation.counter

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.stats.CurrentOuting
import dev.catsradar.domain.stats.Milestone
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.statistics.MilestoneState
import dev.catsradar.presentation.statistics.toRateState
import kotlinx.collections.immutable.toImmutableList

class CounterStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
) {
    fun initial(): CounterState = CounterState(totalLabel = "", count = null, undoVisible = false)

    @Suppress("LongParameterList") // one parameter per thing the Counter shows
    fun map(
        count: Int,
        undoVisible: Boolean,
        locationPermissionHintVisible: Boolean = false,
        currentOuting: CurrentOuting? = null,
        tapBurst: Int? = null,
        lastCoat: CoatOption? = null,
        walkingMode: Boolean = false,
        importProgress: ImportProgressState? = null,
        importSummary: ImportSummaryState? = null,
        coatPrompt: CoatPromptState? = null,
        milestone: Milestone? = null,
        milestoneMoment: Int? = null,
    ): CounterState = CounterState(
        totalLabel = count.toString(),
        count = count,
        undoVisible = undoVisible,
        locationPermissionHintVisible = locationPermissionHintVisible,
        currentOuting = currentOuting?.toState(),
        tapBurst = tapBurst,
        lastCoat = lastCoat,
        walkingMode = walkingMode,
        importProgress = importProgress,
        importSummary = importSummary,
        coatPrompt = coatPrompt,
        // With no cats the first rung is not a milestone to reach, so the state carries none.
        milestone = milestone?.takeIf { count > 0 }?.toState(),
        milestoneMoment = milestoneMoment?.let(::MilestoneMomentState),
    )

    fun importSummary(
        addedCount: Int,
        skipped: Int,
        failed: Int,
        thumbPaths: List<String>,
    ): ImportSummaryState = ImportSummaryState(
        added = addedCount,
        skipped = skipped.takeIf { it > 0 },
        failed = failed.takeIf { it > 0 },
        undoable = addedCount > 0,
        thumbPaths = thumbPaths.map(photoStorage::resolve).toImmutableList(),
    )

    fun coatPrompt(encounter: Encounter): CoatPromptState =
        CoatPromptState(
            catId = encounter.id,
            photoId = encounter.cover?.id,
            thumbPath = encounter.cover?.thumbPath?.let(photoStorage::resolve),
        )

    private fun Milestone.toState(): CounterMilestoneState = CounterMilestoneState(
        next = MilestoneState(valueLabel = value.toString(), remainingLabel = remaining.toString()),
        fraction = (value - remaining - reached).toFloat() / (value - reached),
    )

    private fun CurrentOuting.toState(): CurrentOutingState = CurrentOutingState(
        count = count,
        elapsedLabel = dateTimeFormatter.duration(elapsed),
        rate = rate?.toRateState(),
    )
}
