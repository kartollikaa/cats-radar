package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.stats.Stats
import dev.catsradar.domain.stats.StatsCalculator
import dev.catsradar.domain.time.today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

class ObserveStats(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    // The outing in progress is measured against "now", so it goes stale on its own between cats;
    // ticking is what makes its elapsed time and rate move while nothing is being logged.
    private val ticks: Flow<Unit> = ticker(TickPeriod),
) {
    operator fun invoke(): Flow<Stats> =
        combine(encounterRepository.observeAll(), ticks) { encounters, _ ->
            StatsCalculator.calculate(encounters, today = clock.today(timeZone), now = clock.now())
        }
}
