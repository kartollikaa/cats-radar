package dev.catsradar.app.worker

import dev.catsradar.domain.usecase.ObserveUntriedPlaceCells
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

fun interface PlaceNamingScheduler {
    fun nameUntriedCells()
}

/** Asks for a naming pass whenever a cell nobody has looked up appears, and at start if one already waits. */
class PlaceNamingTrigger(
    private val observeUntriedPlaceCells: ObserveUntriedPlaceCells,
    private val placeNamingScheduler: PlaceNamingScheduler,
) {
    fun start(scope: CoroutineScope): Job {
        var seen = emptySet<String>()
        return observeUntriedPlaceCells()
            .onEach { untried ->
                if (!seen.containsAll(untried)) placeNamingScheduler.nameUntriedCells()
                seen = untried
            }
            .launchIn(scope)
    }
}
