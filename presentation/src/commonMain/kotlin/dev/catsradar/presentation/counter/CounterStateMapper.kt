package dev.catsradar.presentation.counter

class CounterStateMapper {
    fun map(count: Int, undoVisible: Boolean, locationPermissionHintVisible: Boolean = false): CounterState =
        CounterState(
            totalLabel = count.toString(),
            undoVisible = undoVisible,
            locationPermissionHintVisible = locationPermissionHintVisible,
        )
}
