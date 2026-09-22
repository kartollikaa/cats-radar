package dev.catsradar.presentation.counter

import dev.catsradar.presentation.Store

class CounterStore(
    stateMapper: CounterStateMapper,
) : Store<CounterState, CounterIntent, CounterEffect>(stateMapper.map(count = 0)) {
    override suspend fun handle(intent: CounterIntent) = Unit
}
