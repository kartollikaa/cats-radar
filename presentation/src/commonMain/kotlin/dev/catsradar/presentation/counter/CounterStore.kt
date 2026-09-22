package dev.catsradar.presentation.counter

import dev.catsradar.presentation.Store

class CounterStore : Store<CounterState, CounterIntent, CounterEffect>(CounterState()) {
    override suspend fun handle(intent: CounterIntent) = Unit
}
