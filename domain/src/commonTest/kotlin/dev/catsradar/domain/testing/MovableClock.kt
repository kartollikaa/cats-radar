package dev.catsradar.domain.testing

import kotlin.time.Clock
import kotlin.time.Instant

class MovableClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}
