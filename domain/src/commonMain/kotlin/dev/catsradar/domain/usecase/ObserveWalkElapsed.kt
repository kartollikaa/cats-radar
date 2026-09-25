package dev.catsradar.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock
import kotlin.time.Duration

/** How long the walk that is on has lasted so far, moving with the clock; null while no walk is on. */
class ObserveWalkElapsed(
    private val observeOpenWalk: ObserveOpenWalk,
    private val clock: Clock,
    private val ticks: Flow<Unit> = ticker(TickPeriod),
) {
    operator fun invoke(): Flow<Duration?> =
        combine(observeOpenWalk(), ticks) { walk, _ ->
            walk?.let { (clock.now() - it.startedAt).coerceAtLeast(Duration.ZERO) }
        }
}
