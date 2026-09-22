package dev.catsradar.presentation.counter

import dev.catsradar.domain.stats.CurrentOuting
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.statistics.toRateState

class CounterStateMapper(private val dateTimeFormatter: DateTimeFormatter) {
    fun map(
        count: Int,
        undoVisible: Boolean,
        locationPermissionHintVisible: Boolean = false,
        currentOuting: CurrentOuting? = null,
        tapBurst: Int? = null,
    ): CounterState = CounterState(
        totalLabel = count.toString(),
        undoVisible = undoVisible,
        locationPermissionHintVisible = locationPermissionHintVisible,
        currentOuting = currentOuting?.toState(),
        tapBurst = tapBurst,
    )

    private fun CurrentOuting.toState(): CurrentOutingState = CurrentOutingState(
        count = count,
        elapsedLabel = dateTimeFormatter.duration(elapsed),
        rate = rate?.toRateState(),
    )
}
