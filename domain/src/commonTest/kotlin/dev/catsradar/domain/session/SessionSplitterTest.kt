package dev.catsradar.domain.session

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.Session
import dev.catsradar.domain.testing.encounterAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SessionSplitterTest {
    @Test
    fun `empty input produces no sessions`() {
        assertEquals(emptyList(), SessionSplitter.split(emptyList()))
    }

    @Test
    fun `a single encounter is one session with zero duration`() {
        val sessions = SessionSplitter.split(listOf(encounterAt(BASE)))

        assertEquals(listOf(Session(1, BASE, BASE, Duration.ZERO)), sessions)
    }

    @Test
    fun `a gap exactly equal to SESSION_GAP stays one session`() {
        val end = BASE + Tuning.SESSION_GAP
        val sessions = SessionSplitter.split(listOf(encounterAt(BASE), encounterAt(end)))

        assertEquals(listOf(Session(2, BASE, end, Tuning.SESSION_GAP)), sessions)
    }

    @Test
    fun `a gap one millisecond over SESSION_GAP starts a new session`() {
        val secondStart = BASE + Tuning.SESSION_GAP + 1.milliseconds
        val sessions = SessionSplitter.split(listOf(encounterAt(BASE), encounterAt(secondStart)))

        assertEquals(
            listOf(
                Session(1, BASE, BASE, Duration.ZERO),
                Session(1, secondStart, secondStart, Duration.ZERO),
            ),
            sessions,
        )
    }

    @Test
    fun `unsorted input yields the same sessions as sorted input`() {
        val first = encounterAt(BASE)
        val second = encounterAt(BASE + 10.minutes)
        val third = encounterAt(BASE + 1.hours) // 50 min after `second`, past SESSION_GAP
        val expected = listOf(
            Session(2, BASE, BASE + 10.minutes, 10.minutes),
            Session(1, BASE + 1.hours, BASE + 1.hours, Duration.ZERO),
        )

        assertEquals(expected, SessionSplitter.split(listOf(first, second, third)))
        assertEquals(expected, SessionSplitter.split(listOf(third, first, second)))
    }

    @Test
    fun `a soft-deleted encounter is excluded from count and duration`() {
        val kept = encounterAt(BASE)
        val deleted = encounterAt(BASE + 15.minutes, deletedAt = BASE + 1.hours)

        val sessions = SessionSplitter.split(listOf(kept, deleted))

        assertEquals(listOf(Session(1, BASE, BASE, Duration.ZERO)), sessions)
    }

    @Test
    fun `groupByOuting returns each outing's own encounters, not just their aggregate`() {
        val first = encounterAt(BASE)
        val second = encounterAt(BASE + 10.minutes)
        val third = encounterAt(BASE + 1.hours) // past SESSION_GAP from `second`

        val outings = SessionSplitter.groupByOuting(listOf(third, first, second))

        assertEquals(listOf(listOf(first, second), listOf(third)), outings)
    }

    @Test
    fun `groupByOuting excludes a soft-deleted encounter from its outing`() {
        val kept = encounterAt(BASE)
        val deleted = encounterAt(BASE + 15.minutes, deletedAt = BASE + 1.hours)

        val outings = SessionSplitter.groupByOuting(listOf(kept, deleted))

        assertEquals(listOf(listOf(kept)), outings)
    }

    private companion object {
        val BASE = Instant.parse("2026-09-21T10:00:00Z")
    }
}
