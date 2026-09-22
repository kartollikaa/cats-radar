package dev.catsradar.presentation.counter

class CounterStateMapper {
    fun map(count: Int, undoVisible: Boolean): CounterState =
        CounterState(totalLabel = count.toString(), undoVisible = undoVisible)
}
