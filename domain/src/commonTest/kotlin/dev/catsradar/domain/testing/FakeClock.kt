package dev.catsradar.domain.testing

import kotlin.time.Clock
import kotlin.time.Instant

class FakeClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
