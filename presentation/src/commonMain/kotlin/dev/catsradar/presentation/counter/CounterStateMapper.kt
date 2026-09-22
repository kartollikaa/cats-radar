package dev.catsradar.presentation.counter

class CounterStateMapper {
    fun map(count: Int): CounterState = CounterState(totalLabel = count.toString())
}
