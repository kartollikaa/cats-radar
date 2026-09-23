package dev.catsradar.data.platform

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FixTimeTest {

    @Test
    fun aFixIsDatedOnThePhonesClockByHowLongAgoItWasTaken() {
        val taken = fixTime(
            now = NOW,
            nowUptimeNanos = 50.seconds.inWholeNanoseconds,
            fixUptimeNanos = 47.seconds.inWholeNanoseconds,
            fixUtcMillis = (NOW + 2.hours).toEpochMilliseconds(),
        )

        assertEquals(NOW - 3.seconds, taken)
    }

    @Test
    fun aFixWithNoUptimeStampKeepsTheTimeItCarries() {
        val carried = NOW - 10.seconds

        val taken = fixTime(
            now = NOW,
            nowUptimeNanos = 50.seconds.inWholeNanoseconds,
            fixUptimeNanos = 0,
            fixUtcMillis = carried.toEpochMilliseconds(),
        )

        assertEquals(carried, taken)
    }

    @Test
    fun aFixStampedAfterNowOnTheUptimeClockIsDatedNow() {
        val taken = fixTime(
            now = NOW,
            nowUptimeNanos = 50.seconds.inWholeNanoseconds,
            fixUptimeNanos = 51.seconds.inWholeNanoseconds,
            fixUtcMillis = 0,
        )

        assertEquals(NOW, taken)
    }

    private companion object {
        val NOW = Instant.parse("2026-09-23T10:00:00Z")
    }
}
