package dev.catsradar.presentation.counter

import dev.catsradar.domain.stats.CurrentOuting
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.statistics.toRateState
import kotlin.time.Duration

class CounterStateMapper(private val dateTimeFormatter: DateTimeFormatter) {
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
        walkElapsedLabel: String? = null,
        importProgress: ImportProgressState? = null,
        importSummary: ImportSummaryState? = null,
    ): CounterState = CounterState(
        totalLabel = count.toString(),
        count = count,
        undoVisible = undoVisible,
        locationPermissionHintVisible = locationPermissionHintVisible,
        currentOuting = currentOuting?.toState(),
        tapBurst = tapBurst,
        lastCoat = lastCoat,
        walkingMode = walkingMode,
        walkElapsedLabel = walkElapsedLabel,
        importProgress = importProgress,
        importSummary = importSummary,
    )

    fun walkElapsedLabel(walking: Boolean, elapsed: Duration?): String? =
        elapsed?.takeIf { walking }?.let(dateTimeFormatter::duration)

    fun importSummary(addedCount: Int, skipped: Int, failed: Int): ImportSummaryState = ImportSummaryState(
        added = addedCount,
        skipped = skipped.takeIf { it > 0 },
        failed = failed.takeIf { it > 0 },
        undoable = addedCount > 0,
    )

    private fun CurrentOuting.toState(): CurrentOutingState = CurrentOutingState(
        count = count,
        elapsedLabel = dateTimeFormatter.duration(elapsed),
        rate = rate?.toRateState(),
    )
}
