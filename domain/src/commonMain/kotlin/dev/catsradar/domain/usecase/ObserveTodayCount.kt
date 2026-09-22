package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.time.localDate
import dev.catsradar.domain.time.today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * How many cats today, and nothing else.
 *
 * [ObserveStats] answers this too, but it carries a clock tick for elapsed times and rates that a
 * bare count has no use for.
 */
class ObserveTodayCount(
    private val encounterRepository: EncounterRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(): Flow<Int> =
        encounterRepository.observeAll()
            // Each encounter's own day, in the offset it was captured at, against the device's today.
            .map { encounters -> encounters.count { it.localDate() == clock.today(timeZone) } }
            .distinctUntilChanged()
}
