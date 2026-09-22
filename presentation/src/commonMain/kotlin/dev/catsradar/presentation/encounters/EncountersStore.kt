package dev.catsradar.presentation.encounters

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

class EncountersStore(
    observeEncounters: ObserveEncounters,
    private val stateMapper: EncountersStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<EncountersState, EncountersIntent, EncountersEffect>(EncountersState()) {

    init {
        observeEncounters()
            .onEach { encounters -> setState { stateMapper.map(encounters, clock.today(timeZone)) } }
            .launchIn(viewModelScope)
    }

    @Suppress("EmptyFunctionBlock") // EncountersIntent has no members: this screen dispatches none
    override suspend fun handle(intent: EncountersIntent) {
    }
}
