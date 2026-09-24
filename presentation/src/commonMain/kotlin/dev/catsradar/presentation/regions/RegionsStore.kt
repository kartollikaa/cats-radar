package dev.catsradar.presentation.regions

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.time.today
import dev.catsradar.domain.usecase.ObserveRegion
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

class RegionsStore(
    parent: RegionKey?,
    observeRegion: ObserveRegion,
    private val stateMapper: RegionsStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<RegionsState, RegionsIntent, RegionsEffect>(RegionsState.Loading) {

    init {
        observeRegion(parent)
            .onEach { view -> setState { stateMapper.map(view, clock.today(timeZone)) } }
            .launchIn(viewModelScope)
    }

    @Suppress("EmptyFunctionBlock") // RegionsIntent has no members: navigation is the host's job
    override suspend fun handle(intent: RegionsIntent) {
    }
}
