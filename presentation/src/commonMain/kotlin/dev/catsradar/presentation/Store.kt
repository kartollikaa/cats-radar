package dev.catsradar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class Store<State, Intent, Effect>(initial: State) : ViewModel() {

    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val effectChannel = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = effectChannel.receiveAsFlow()

    fun dispatch(intent: Intent) {
        viewModelScope.launch { handle(intent) }
    }

    protected abstract suspend fun handle(intent: Intent)

    protected fun setState(reduce: State.() -> State) {
        mutableState.update(reduce)
    }

    protected suspend fun emit(effect: Effect) {
        effectChannel.send(effect)
    }
}
