package dev.catsradar.domain.backup

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WalkMergeTest {

    private fun walk(id: String, endedAt: Instant? = START + 1.hours, updatedAt: Instant = START + 1.hours) =
        Walk(id, START, endedAt, "device", START, updatedAt)

    private fun point(walkId: String, minute: Int) =
        TrackPoint(walkId, START + minute.minutes, 41.0 + minute * 0.001, 2.0, 5f)

    private fun merge(
        localWalks: List<Walk> = emptyList(),
        localPoints: List<TrackPoint> = emptyList(),
        importedWalks: List<Walk> = emptyList(),
        importedPoints: List<TrackPoint> = emptyList(),
    ) = BackupMerge.merge(
        local = BackupContents(walks = localWalks, trackPoints = localPoints),
        imported = BackupContents(walks = importedWalks, trackPoints = importedPoints),
    )

    @Test
    fun `a new walk the archive lists twice arrives once, as its later edit listed first`() {
        val later = walk("w", endedAt = START + 2.hours, updatedAt = START + 2.hours)

        val result = merge(importedWalks = listOf(later, walk("w")))

        assertEquals(listOf(later), result.walks)
    }

    @Test
    fun `a new walk the archive lists twice arrives once, as its later edit listed last`() {
        val later = walk("w", endedAt = START + 2.hours, updatedAt = START + 2.hours)

        val result = merge(importedWalks = listOf(walk("w"), later))

        assertEquals(listOf(later), result.walks)
    }

    @Test
    fun `a new walk the archive lists twice with the same edit time arrives as the copy listed first`() {
        val first = walk("w", endedAt = START + 2.hours)
        val second = walk("w", endedAt = START + 3.hours)

        val result = merge(importedWalks = listOf(first, second))

        assertEquals(listOf(first), result.walks)
    }

    @Test
    fun `a walk here that the archive lists twice takes only its later edit`() {
        val latest = walk("w", endedAt = START + 3.hours, updatedAt = START + 3.hours)
        val older = walk("w", endedAt = START + 2.hours, updatedAt = START + 2.hours)

        val result = merge(localWalks = listOf(walk("w")), importedWalks = listOf(latest, older))

        assertEquals(listOf(latest), result.walks)
    }

    @Test
    fun `a point that is not on the globe is left out, and the rest of its route arrives`() {
        val offGlobe = point("w", 2).copy(lat = 95.0)

        val result = merge(importedWalks = listOf(walk("w")), importedPoints = listOf(point("w", 1), offGlobe))

        assertEquals(listOf(walk("w")), result.walks)
        assertEquals(listOf(point("w", 1)), result.trackPoints)
    }

    @Test
    fun `a walk this device has never seen arrives with its route`() {
        val result = merge(importedWalks = listOf(walk("w")), importedPoints = listOf(point("w", 1), point("w", 2)))

        assertEquals(listOf(walk("w")), result.walks)
        assertEquals(listOf(point("w", 1), point("w", 2)), result.trackPoints)
    }

    @Test
    fun `for a walk both know, the later edit wins and a tie or an older one keeps the walk here`() {
        val laterEdit = walk("later", endedAt = START + 2.hours, updatedAt = START + 2.hours)
        val result = merge(
            localWalks = listOf(walk("later"), walk("tie"), walk("older", updatedAt = START + 2.hours)),
            importedWalks = listOf(laterEdit, walk("tie", endedAt = START + 90.minutes), walk("older")),
        )

        assertEquals(listOf(laterEdit), result.walks)
    }

    @Test
    fun `a route is never shortened, and no point is written twice`() {
        val longer = merge(
            localWalks = listOf(walk("w")),
            localPoints = listOf(point("w", 1), point("w", 2)),
            importedWalks = listOf(walk("w")),
            importedPoints = listOf(point("w", 1), point("w", 2), point("w", 3), point("w", 3)),
        )
        val shorter = merge(
            localWalks = listOf(walk("w")),
            localPoints = listOf(point("w", 1), point("w", 2), point("w", 3)),
            importedWalks = listOf(walk("w")),
            importedPoints = listOf(point("w", 1)),
        )

        assertEquals(listOf(point("w", 3)), longer.trackPoints)
        assertEquals(emptyList(), longer.walks)
        assertEquals(emptyList(), shorter.trackPoints)
    }

    @Test
    fun `a walk still on in the archive arrives ended at its last point, or at its start with none`() {
        val result = merge(
            importedWalks = listOf(
                walk("on", endedAt = null, updatedAt = START),
                walk("bare", endedAt = null, updatedAt = START),
            ),
            importedPoints = listOf(point("on", 1), point("on", 3)),
        )

        assertEquals(
            listOf(
                walk("on", endedAt = START + 3.minutes, updatedAt = START),
                walk("bare", endedAt = START, updatedAt = START),
            ),
            result.walks,
        )
    }

    @Test
    fun `the walk on here stays on when an archive carries it too, and gains the points it lacked`() {
        val onHere = walk("here", endedAt = null, updatedAt = START)

        val result = merge(
            localWalks = listOf(onHere),
            localPoints = listOf(point("here", 1)),
            importedWalks = listOf(onHere),
            importedPoints = listOf(point("here", 1), point("here", 2)),
        )

        assertEquals(emptyList(), result.walks)
        assertEquals(listOf(point("here", 2)), result.trackPoints)
    }

    @Test
    fun `a walk ended on arrival reaches the end of a longer route a later archive brings`() {
        val endedOnArrival = walk("w", endedAt = START + 2.minutes, updatedAt = START)

        val result = merge(
            localWalks = listOf(endedOnArrival),
            localPoints = listOf(point("w", 1), point("w", 2)),
            importedWalks = listOf(walk("w", endedAt = null, updatedAt = START)),
            importedPoints = listOf(point("w", 1), point("w", 2), point("w", 5)),
        )

        assertEquals(listOf(endedOnArrival.copy(endedAt = START + 5.minutes)), result.walks)
        assertEquals(listOf(point("w", 5)), result.trackPoints)
    }

    @Test
    fun `importing the current state writes no walk and no point`() {
        val walks = listOf(walk("a"), walk("b", endedAt = null, updatedAt = START))
        val points = listOf(point("a", 1), point("b", 2))

        val result = merge(localWalks = walks, localPoints = points, importedWalks = walks, importedPoints = points)

        assertEquals(emptyList(), result.walks)
        assertEquals(emptyList(), result.trackPoints)
    }

    @Test
    fun `a point whose walk is in neither the archive nor here is left out`() {
        val result = merge(importedPoints = listOf(point("nowhere", 1)))

        assertEquals(emptyList(), result.trackPoints)
    }

    private companion object {
        val START = Instant.parse("2026-09-23T09:00:00Z")
    }
}
