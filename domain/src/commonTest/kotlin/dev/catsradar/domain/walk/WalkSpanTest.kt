package dev.catsradar.domain.walk

import dev.catsradar.domain.model.Walk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class WalkSpanTest {

    private fun walk(endedAt: Instant? = END) = Walk("w", START, endedAt, "device", START, endedAt ?: START)

    @Test
    fun `a span touching either end of a walk overlaps it`() {
        assertTrue(walk().overlaps(START - 1.hours, START))
        assertTrue(walk().overlaps(END, END + 1.hours))
        assertTrue(walk().overlaps(START - 1.hours, END + 1.hours))
    }

    @Test
    fun `a span wholly before or after a walk does not overlap it`() {
        assertFalse(walk().overlaps(START - 2.hours, START - 1.milliseconds))
        assertFalse(walk().overlaps(END + 1.milliseconds, END + 1.hours))
    }

    @Test
    fun `a walk still on overlaps any span that ends after its start`() {
        assertTrue(walk(endedAt = null).overlaps(START + 10.days, START + 11.days))
        assertFalse(walk(endedAt = null).overlaps(START - 2.hours, START - 1.milliseconds))
    }

    private companion object {
        val START = Instant.parse("2026-09-24T10:00:00Z")
        val END = Instant.parse("2026-09-24T11:00:00Z")
    }
}
