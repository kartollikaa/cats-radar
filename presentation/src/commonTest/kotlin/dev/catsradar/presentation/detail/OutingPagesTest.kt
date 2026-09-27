package dev.catsradar.presentation.detail

import dev.catsradar.presentation.encounters.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OutingPagesTest {

    private val e1 = cat("e1", 0.minutes)
    private val e2 = cat("e2", 10.minutes)
    private val m1 = cat("m1", 3.hours)
    private val m2 = cat("m2", 3.hours + 10.minutes)
    private val m3 = cat("m3", 3.hours + 20.minutes)
    private val m4 = cat("m4", 3.hours + 30.minutes)
    private val l1 = cat("l1", 6.hours)
    private val l2 = cat("l2", 6.hours + 10.minutes)
    private val all = listOf(e1, e2, m1, m2, m3, m4, l1, l2)

    @Test
    fun `it starts on the restored cat while that cat is live`() {
        val pages = OutingPages(openedId = "m1", restoredId = "m4")

        assertEquals("m4", pages.update(all)?.currentId)
    }

    @Test
    fun `it starts on the opened cat when the restored one is deleted`() {
        val pages = OutingPages(openedId = "m1", restoredId = "m4")

        val shown = pages.update(all.map { if (it.id == "m4") it.copy(deletedAt = START) else it })

        assertEquals("m1", shown?.currentId)
    }

    @Test
    fun `with neither cat live there are no pages until the opened one is`() {
        val pages = OutingPages(openedId = "m1", restoredId = null)

        assertNull(pages.update(all - m1))
        assertEquals("m1", pages.update(all)?.currentId)
    }

    @Test
    fun `the pages are the outing of the cat on screen, newest first`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)

        assertEquals(listOf(m4, m3, m2, m1), pages.update(all)?.window?.cats)
    }

    @Test
    fun `a cat logged into the outing joins the pages, and the cat on screen stays`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)
        pages.update(all)
        val logged = cat("m5", 3.hours + 40.minutes)

        val shown = pages.update(all + logged)

        assertEquals(listOf(logged, m4, m3, m2, m1), shown?.window?.cats)
        assertEquals("m2", shown?.currentId)
    }

    @Test
    fun `an outing a delete splits in two stays whole on the pages`() {
        val glued = listOf(cat("a", 0.minutes), cat("b", 25.minutes), cat("c", 50.minutes))
        val pages = OutingPages(openedId = "a", restoredId = null)
        pages.update(glued)

        val shown = pages.update(glued.filterNot { it.id == "b" })

        assertEquals(listOf("c", "a"), shown?.window?.cats?.map { it.id })
    }

    @Test
    fun `the cat on screen deleted hands the screen to the next older page`() {
        val pages = OutingPages(openedId = "m3", restoredId = null)
        pages.update(all)

        assertEquals("m2", pages.update(all - m3)?.currentId)
    }

    @Test
    fun `the oldest page deleted hands the screen to the newer one`() {
        val pages = OutingPages(openedId = "m1", restoredId = null)
        pages.update(all)

        assertEquals("m2", pages.update(all - m1)?.currentId)
    }

    @Test
    fun `the cat on screen deleted with its older neighbour hands the screen to the next older page left`() {
        val pages = OutingPages(openedId = "m3", restoredId = null)
        pages.update(all)

        assertEquals("m1", pages.update(all - m3 - m2)?.currentId)
    }

    @Test
    fun `with every page deleted there are none, and a cat brought back rejoins them`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)
        pages.update(all)

        assertNull(pages.update(listOf(e1, e2, l1, l2)))
        val shown = pages.update(listOf(e1, e2, m3, l1, l2))

        assertEquals(listOf(m3), shown?.window?.cats)
        assertEquals("m3", shown?.currentId)
    }

    @Test
    fun `settling moves the screen to a cat on the pages and ignores any other`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)
        pages.update(all)

        pages.settle("m4")
        pages.settle("l1")

        assertEquals("m4", pages.shown?.currentId)
    }

    @Test
    fun `releasing towards the older outing moves the pages there, onto its newest cat`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)
        pages.update(all)

        val shown = pages.release(OutingDirection.OLDER)

        assertEquals(listOf(e2, e1), shown?.window?.cats)
        assertEquals("e2", shown?.currentId)
    }

    @Test
    fun `releasing towards the newer outing moves the pages there, onto its oldest cat`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)
        pages.update(all)

        val shown = pages.release(OutingDirection.NEWER)

        assertEquals(listOf(l2, l1), shown?.window?.cats)
        assertEquals("l1", shown?.currentId)
    }

    @Test
    fun `releasing past the newest outing changes nothing`() {
        val pages = OutingPages(openedId = "l1", restoredId = null)
        val before = pages.update(all)

        assertNull(pages.release(OutingDirection.NEWER))
        assertEquals(before, pages.shown)
    }

    private fun cat(id: String, at: Duration) = encounterFixture(id, START + at)

    private companion object {
        val START = Instant.parse("2026-09-22T06:00:00Z")
    }
}
