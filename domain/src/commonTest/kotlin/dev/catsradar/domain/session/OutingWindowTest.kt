package dev.catsradar.domain.session

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.testing.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OutingWindowTest {

    private val e1 = cat("e1", 0.minutes)
    private val e2 = cat("e2", 10.minutes)
    private val m1 = cat("m1", 3.hours)
    private val m2 = cat("m2", 3.hours + 10.minutes)
    private val m3 = cat("m3", 3.hours + 20.minutes)
    private val l1 = cat("l1", 6.hours)
    private val l2 = cat("l2", 6.hours + 10.minutes)
    private val all = listOf(e1, e2, m1, m2, m3, l1, l2)

    @Test
    fun `the window is the outing holding the shown cat, newest first`() {
        assertEquals(listOf(m3, m2, m1), outingWindow(all, setOf("m2"))?.cats)
    }

    @Test
    fun `the outings on either side are its neighbours, oldest first`() {
        val window = outingWindow(all, setOf("m2"))

        assertEquals(listOf(l1, l2), window?.newer)
        assertEquals(listOf(e1, e2), window?.older)
    }

    @Test
    fun `the newest and the oldest outing have nothing beyond them`() {
        assertNull(outingWindow(all, setOf("l1"))?.newer)
        assertEquals(listOf(m1, m2, m3), outingWindow(all, setOf("l1"))?.older)
        assertNull(outingWindow(all, setOf("e2"))?.older)
        assertEquals(
            OutingWindow(cats = listOf(m3, m2, m1), newer = null, older = null),
            outingWindow(listOf(m1, m2, m3), setOf("m1")),
        )
    }

    @Test
    fun `a move lands on the neighbour's cat nearest the window`() {
        val window = outingWindow(all, setOf("m2"))

        assertEquals(l1, window?.newerLanding)
        assertEquals(e2, window?.olderLanding)
        assertNull(outingWindow(all, setOf("l2"))?.newerLanding)
        assertNull(outingWindow(all, setOf("e1"))?.olderLanding)
    }

    @Test
    fun `an outing a delete split in two stays whole while a cat of each half is shown`() {
        val a = cat("a", 0.minutes)
        val gone = cat("gone", 25.minutes).deleted()
        val b = cat("b", 50.minutes)

        assertEquals(
            OutingWindow(cats = listOf(b, a), newer = null, older = null),
            outingWindow(listOf(a, gone, b), setOf("a", "b")),
        )
    }

    @Test
    fun `showing one half of a split outing gives that half, the other beside it`() {
        val a = cat("a", 0.minutes)
        val gone = cat("gone", 25.minutes).deleted()
        val b = cat("b", 50.minutes)

        assertEquals(
            OutingWindow(cats = listOf(a), newer = listOf(b), older = null),
            outingWindow(listOf(a, gone, b), setOf("a")),
        )
    }

    @Test
    fun `a cat logged into the outing joins the window`() {
        val m4 = cat("m4", 3.hours + 40.minutes)

        assertEquals(listOf(m4, m3, m2, m1), outingWindow(all + m4, setOf("m1", "m2", "m3"))?.cats)
    }

    @Test
    fun `an outing a new cat merges into the window joins it`() {
        val n1 = cat("n1", 4.hours + 15.minutes)
        val n2 = cat("n2", 4.hours + 25.minutes)
        val bridge = cat("bridge", 3.hours + 48.minutes)
        val before = outingWindow(all + n1 + n2, setOf("m1", "m2", "m3"))
        val after = outingWindow(all + n1 + n2 + bridge, setOf("m1", "m2", "m3"))

        assertEquals(listOf(n1, n2), before?.newer)
        assertEquals(listOf(n2, n1, bridge, m3, m2, m1), after?.cats)
        assertEquals(listOf(l1, l2), after?.newer)
    }

    @Test
    fun `an outing between two shown ones is on the window`() {
        assertEquals(
            OutingWindow(cats = listOf(l2, l1, m3, m2, m1, e2, e1), newer = null, older = null),
            outingWindow(all, setOf("e1", "l1")),
        )
    }

    @Test
    fun `a deleted cat is never on the window or beside it`() {
        val window = outingWindow(
            listOf(e1, e2.deleted(), m1, m2.deleted(), m3, l1, l2),
            setOf("m1", "m2", "m3"),
        )

        assertEquals(listOf(m3, m1), window?.cats)
        assertEquals(listOf(e1), window?.older)
    }

    @Test
    fun `no live shown cat gives no window`() {
        assertNull(outingWindow(all, emptySet()))
        assertNull(outingWindow(all, setOf("unknown")))
        assertNull(outingWindow(listOf(m1.deleted(), m2), setOf("m1")))
        assertNull(outingWindow(emptyList(), setOf("m1")))
    }

    @Test
    fun `input order does not change the window`() {
        assertEquals(listOf(m3, m2, m1), outingWindow(all.reversed(), setOf("m2"))?.cats)
        assertEquals(outingWindow(all, setOf("m2")), outingWindow(all.reversed(), setOf("m2")))
    }

    private fun cat(id: String, after: Duration): Encounter = encounterFixture(id, BASE + after)

    private fun Encounter.deleted(): Encounter = copy(deletedAt = occurredAt + 1.hours)

    private companion object {
        val BASE = Instant.parse("2026-09-21T07:00:00Z")
    }
}
