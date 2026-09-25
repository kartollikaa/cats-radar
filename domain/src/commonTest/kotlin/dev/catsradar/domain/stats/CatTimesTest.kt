package dev.catsradar.domain.stats

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.testing.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class CatTimesTest {

    private fun walk(start: Instant, end: Instant?) = Walk("w", start, end, "device", start, end ?: start)

    private fun cat(id: String, at: Instant) = encounterFixture(id, at)

    private fun assertCounts(expected: Int, walk: Walk, cats: List<Encounter>) {
        val scanned = cats.count { cat ->
            val at = cat.occurredAt
            cat.deletedAt == null && at >= walk.startedAt && walk.endedAt.let { it == null || at <= it }
        }
        assertEquals(expected, scanned, "the fixture does not show what its test claims")
        assertEquals(expected, CatTimes.of(cats).countWithin(walk.startedAt, walk.endedAt))
    }

    @Test
    fun `a cat at the very start or the very end of a walk counts, and one a moment outside does not`() {
        val walk = walk(START, START + 1.hours)
        val cats = listOf(
            cat("after", START + 1.hours + 1.milliseconds),
            cat("end", START + 1.hours),
            cat("middle", START + 30.minutes),
            cat("start", START),
            cat("before", START - 1.milliseconds),
        )

        assertCounts(3, walk, cats)
    }

    @Test
    fun `a walk still on counts every cat from its start on`() {
        val cats = listOf(cat("before", START - 1.minutes), cat("start", START), cat("much later", START + 48.hours))

        assertCounts(2, walk(START, end = null), cats)
    }

    @Test
    fun `a deleted cat does not count`() {
        val cats = listOf(cat("live", START + 1.minutes), cat("deleted", START + 2.minutes).copy(deletedAt = START))

        assertCounts(1, walk(START, START + 1.hours), cats)
    }

    @Test
    fun `several cats at one instant all count at either end of a walk`() {
        val end = START + 1.hours
        val cats = listOf(
            cat("s1", START),
            cat("s2", START),
            cat("s3", START),
            cat("e1", end),
            cat("e2", end),
            cat("after", end + 1.milliseconds),
            cat("before", START - 1.milliseconds),
        )

        assertCounts(5, walk(START, end), cats)
    }

    @Test
    fun `a walk that ends before it starts counts no cat`() {
        val cats = listOf(cat("a", START - 30.minutes), cat("b", START), cat("c", START - 1.hours))

        assertCounts(0, walk(START, START - 1.hours), cats)
    }

    @Test
    fun `with no cat, a walk counts none`() {
        assertCounts(0, walk(START, START + 1.hours), emptyList())
    }

    private companion object {
        val START = Instant.parse("2026-09-24T10:00:00Z")
    }
}
