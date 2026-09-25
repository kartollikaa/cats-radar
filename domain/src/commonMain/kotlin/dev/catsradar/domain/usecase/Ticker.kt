package dev.catsradar.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val TickPeriod = 10.seconds

internal fun ticker(period: Duration): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(period)
    }
}
