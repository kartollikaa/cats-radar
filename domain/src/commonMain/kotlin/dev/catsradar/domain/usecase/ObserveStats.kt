package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.stats.Stats
import dev.catsradar.domain.stats.StatsCalculator
import dev.catsradar.domain.time.today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

class ObserveStats(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(): Flow<Stats> = encounterRepository.observeAll().map { encounters ->
        val now = clock.now()
        StatsCalculator.calculate(encounters, today = clock.today(timeZone), now = now)
    }
}
