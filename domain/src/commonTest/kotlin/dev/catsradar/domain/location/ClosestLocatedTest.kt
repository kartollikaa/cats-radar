package dev.catsradar.domain.location

import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.locatedFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val LoggedAt = Instant.parse("2026-09-20T08:30:00Z")

class ClosestLocatedTest {

    private val target = encounterFixture(id = "target", occurredAt = LoggedAt)

    private fun located(id: String, occurredAt: Instant) =
        locatedFixture(id = id, occurredAt = occurredAt, lat = 41.39864, lon = 2.17842)

    @Test
    fun `the located cat logged nearest in time wins, before or after`() {
        val before = located("before", LoggedAt - 20.minutes)
        val after = located("after", LoggedAt + 5.minutes)
        val farAfter = located("far-after", LoggedAt + 2.days)

        assertEquals(after, listOf(before, target, farAfter, after).closestLocatedInTime(target))
    }

    @Test
    fun `cats with no location, deleted ones and ones off the globe never count`() {
        val far = located("far", LoggedAt + 3.hours)
        val unlocated = encounterFixture(id = "unlocated", occurredAt = LoggedAt + 1.minutes)
        val deleted = located("deleted", LoggedAt + 2.minutes).copy(deletedAt = LoggedAt + 1.hours)
        val offGlobe = located("off-globe", LoggedAt + 3.minutes).copy(lat = 95.0)
        val markedNone = located("marked-none", LoggedAt + 4.minutes).copy(locationSource = LocationSource.NONE)

        assertEquals(
            far,
            listOf(unlocated, deleted, offGlobe, markedNone, far, target).closestLocatedInTime(target),
        )
    }

    @Test
    fun `the target itself never counts, even with coordinates`() {
        val locatedTarget = located("target", LoggedAt)
        val other = located("other", LoggedAt + 1.hours)

        assertEquals(other, listOf(locatedTarget, other).closestLocatedInTime(locatedTarget))
    }

    @Test
    fun `two cats equally far in time give the earlier one`() {
        val before = located("before", LoggedAt - 10.minutes)
        val after = located("after", LoggedAt + 10.minutes)

        assertEquals(before, listOf(after, before).closestLocatedInTime(target))
    }

    @Test
    fun `no located cat gives nothing`() {
        val unlocated = encounterFixture(id = "unlocated", occurredAt = LoggedAt + 1.minutes)

        assertNull(listOf(target, unlocated).closestLocatedInTime(target))
    }
}
