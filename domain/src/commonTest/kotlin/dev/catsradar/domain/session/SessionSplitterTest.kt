package dev.catsradar.domain.session

import dev.catsradar.domain.Tuning
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

        assertEquals(1, sessions.size)
        assertEquals(1, sessions.single().count)
        assertEquals(Duration.ZERO, sessions.single().duration)
    }

    @Test
    fun `a gap exactly equal to SESSION_GAP stays one session`() {
        val encounters = listOf(encounterAt(BASE), encounterAt(BASE + Tuning.SESSION_GAP))

        assertEquals(1, SessionSplitter.split(encounters).size)
    }

    @Test
    fun `a gap one millisecond over SESSION_GAP starts a new session`() {
        val encounters = listOf(encounterAt(BASE), encounterAt(BASE + Tuning.SESSION_GAP + 1.milliseconds))

        assertEquals(2, SessionSplitter.split(encounters).size)
    }

    @Test
    fun `unsorted input yields the same sessions as sorted input`() {
        val sorted = listOf(encounterAt(BASE), encounterAt(BASE + 10.minutes), encounterAt(BASE + 1.hours))
        val shuffled = listOf(sorted[2], sorted[0], sorted[1])

        assertEquals(SessionSplitter.split(sorted), SessionSplitter.split(shuffled))
    }

    private companion object {
        val BASE = Instant.parse("2026-09-21T10:00:00Z")
    }
}
