package dev.catsradar.domain.stats

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.testing.encounterFixture
import dev.catsradar.domain.testing.withPhoto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.Instant

class StatsCalculatorTest {

    private fun stats(encounters: List<Encounter>, now: Instant = NOW, today: LocalDate = TODAY) =
        StatsCalculator.calculate(encounters, today = today, now = now)

    private fun at(instant: Instant, id: String = "e-$instant", kind: EncounterKind = EncounterKind.TALLY) =
        encounterFixture(id, instant).copy(kind = kind)

    private fun coated(id: String, coat: CatCoat?) = at(NOON, id).copy(coat = coat)

    private fun loggedAt(localTime: String, offset: UtcOffset) =
        at(LocalDateTime.parse(localTime).toInstant(offset), id = "$localTime$offset")
            .copy(tzOffsetMinutes = offset.totalSeconds / 60)

    @Test
    fun `no encounters gives zeroes, no rate and no current outing`() {
        val stats = stats(emptyList())

        assertEquals(0, stats.total)
        assertEquals(0, stats.outings)
        assertEquals(0, stats.currentStreak)
        assertNull(stats.overallRate)
        assertNull(stats.bestOuting)
        assertNull(stats.currentOuting)
    }

    @Test
    fun `soft-deleted encounters count for nothing`() {
        val deleted = encounterFixture("gone", NOON).copy(deletedAt = NOW)

        val stats = stats(listOf(at(NOON), deleted))

        assertEquals(1, stats.total)
    }

    @Test
    fun `today counts only the device's today, and the day windows include today itself`() {
        val encounters = listOf(
            at(NOON),
            at(NOON - 1.days),
            at(NOON - 6.days),
            at(NOON - 7.days),
            at(NOON - 29.days),
            at(NOON - 30.days),
        )

        val stats = stats(encounters)

        assertEquals(1, stats.today)
        // Today plus the six before it, not today plus seven.
        assertEquals(3, stats.lastSevenDays)
        assertEquals(5, stats.lastThirtyDays)
    }

    @Test
    fun `cats with a photo are counted however they were logged`() {
        val stats = stats(
            listOf(
                at(NOON),
                at(NOON - 1.hours, kind = EncounterKind.PHOTO).withPhoto(photoPath = "taken.jpg"),
                at(NOON - 2.hours).withPhoto(photoPath = "attached.jpg"),
                at(NOON - 3.hours, kind = EncounterKind.PHOTO),
            ),
        )

        assertEquals(4, stats.total)
        assertEquals(2, stats.withPhoto)
    }

    @Test
    fun `coats are listed busiest first, and the unnoted ones last however many there are`() {
        val encounters = listOf(
            coated("g", CatCoat.GINGER),
            coated("b1", CatCoat.BLACK),
            coated("b2", CatCoat.BLACK),
            coated("n1", coat = null),
            coated("n2", coat = null),
            coated("n3", coat = null),
        )

        assertEquals(listOf(CatCoat.BLACK, CatCoat.GINGER, null), stats(encounters).byCoat.map { it.coat })
    }

    @Test
    fun `a coat's share is its cats over every cat still here`() {
        val encounters = listOf(
            coated("g", CatCoat.GINGER),
            coated("b1", CatCoat.BLACK),
            coated("b2", CatCoat.BLACK),
            coated("b3", CatCoat.BLACK),
            coated("gone", CatCoat.BLACK).copy(deletedAt = NOW),
        )

        assertEquals(
            listOf(CoatCount(CatCoat.BLACK, 3, 0.75), CoatCount(CatCoat.GINGER, 1, 0.25)),
            stats(encounters).byCoat,
        )
    }

    @Test
    fun `a coat seen only on a deleted cat has no row`() {
        val encounters = listOf(coated("g", CatCoat.GINGER), coated("gone", CatCoat.WHITE).copy(deletedAt = NOW))

        assertEquals(listOf(CatCoat.GINGER), stats(encounters).byCoat.map { it.coat })
    }

    @Test
    fun `a streak ending today counts, and so does one ending yesterday`() {
        val endingToday = stats(listOf(at(NOON), at(NOON - 1.days), at(NOON - 2.days)))
        assertEquals(3, endingToday.currentStreak)

        val endingYesterday = stats(listOf(at(NOON - 1.days), at(NOON - 2.days)))
        assertEquals(2, endingYesterday.currentStreak)
    }

    @Test
    fun `a streak that ended before yesterday is not current`() {
        val stats = stats(listOf(at(NOON - 2.days), at(NOON - 3.days)))

        assertEquals(0, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
    }

    @Test
    fun `the longest streak is the longest run anywhere, not the most recent one`() {
        val old = listOf(10, 11, 12, 13).map { at(NOON - it.days) }
        val recent = listOf(0, 1).map { at(NOON - it.days) }

        val stats = stats(old + recent)

        assertEquals(4, stats.longestStreak)
        assertEquals(2, stats.currentStreak)
    }

    @Test
    fun `each day window starts at the local midnight of the place a cat was logged`() {
        val encounters = listOf(
            loggedAt("2026-09-22T00:01", PLUS_TWO),
            loggedAt("2026-09-21T23:59", PLUS_TWO),
            loggedAt("2026-09-16T00:01", PLUS_TWO),
            loggedAt("2026-09-15T23:59", PLUS_TWO),
            loggedAt("2026-08-24T00:01", PLUS_TWO),
            loggedAt("2026-08-23T23:59", PLUS_TWO),
        )

        val stats = stats(encounters)

        assertEquals(1, stats.today)
        assertEquals(3, stats.lastSevenDays)
        assertEquals(5, stats.lastThirtyDays)
    }

    @Test
    fun `cats a minute either side of local midnight make a two-day streak`() {
        val encounters = listOf(loggedAt("2026-09-21T23:59", PLUS_TWO), loggedAt("2026-09-22T00:01", PLUS_TWO))

        assertEquals(2, stats(encounters).currentStreak)
    }

    @Test
    fun `a streak follows the local days of a trip across zones, not their UTC dates`() {
        // Their UTC dates are the 20th and the 23rd: only the local dates are consecutive.
        val encounters = listOf(loggedAt("2026-09-21T01:00", MOSCOW), loggedAt("2026-09-22T20:00", NEW_YORK))

        val stats = stats(encounters, now = Instant.parse("2026-09-23T02:00:00Z"))

        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
    }

    @Test
    fun `a cat logged last evening in another zone is not today's, though here it is already today`() {
        // 22:00 on the 21st in New York is already the morning of the 22nd in Moscow, where today is.
        val lastEvening = loggedAt("2026-09-21T22:00", NEW_YORK)

        val stats = stats(listOf(lastEvening), now = Instant.parse("2026-09-22T06:00:00Z"))

        assertEquals(0, stats.today)
        assertEquals(1, stats.lastSevenDays)
    }

    @Test
    fun `several cats on one day do not lengthen a streak`() {
        val stats = stats(listOf(at(NOON), at(NOON - 1.hours), at(NOON - 2.hours)))

        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.longestStreak)
    }

    @Test
    fun `the next milestone is the first one above the total, with the distance to it`() {
        assertEquals(Milestone(1, 1), stats(emptyList()).nextMilestone)
        assertEquals(Milestone(10, 9), stats(listOf(at(NOON))).nextMilestone)
    }

    @Test
    fun `a total sitting exactly on a milestone points at the next one, never at itself`() {
        val ten = (1..10).map { at(NOON - (it * 2).hours, id = "e$it") }

        val milestone = assertNotNull(stats(ten).nextMilestone)

        assertEquals(25, milestone.value)
        assertTrue(milestone.remaining > 0)
    }

    @Test
    fun `past the last milestone there is nothing left to reach`() {
        val total = Tuning.MILESTONES.last()
        val encounters = (1..total).map { at(NOON - (it * 2).hours, id = "e$it") }

        assertNull(stats(encounters).nextMilestone)
    }

    @Test
    fun `an outing too short to measure produces no rate at all`() {
        val start = NOON - 2.hours
        val brief = listOf(at(start, "a"), at(start + Tuning.MIN_RATE_DURATION - 1.milliseconds, "b"))

        val stats = stats(brief)

        assertEquals(1, stats.outings)
        assertNull(stats.overallRate)
        assertNull(stats.bestOuting)
    }

    @Test
    fun `an outing exactly as long as the minimum is measured`() {
        val start = NOON - 2.hours
        val minimal = listOf(at(start, "a"), at(start + Tuning.MIN_RATE_DURATION, "b"))

        val rate = assertNotNull(stats(minimal).overallRate)

        assertEquals(2 / Tuning.MIN_RATE_DURATION.toDouble(DurationUnit.HOURS), rate.perHour, 1e-9)
    }

    @Test
    fun `a single cat is never a rate however long ago it was`() {
        val stats = stats(listOf(at(NOON - 5.hours)))

        assertNull(stats.overallRate)
    }

    @Test
    fun `an eligible outing's rate is its cats over its own span`() {
        val start = NOON - 3.hours
        // Six cats, first to last exactly one hour.
        val encounters = (0..5).map { at(start + (it * 12).minutes, "e$it") }

        val rate = assertNotNull(stats(encounters).overallRate)

        assertEquals(6.0, rate.perHour, 1e-9)
        assertEquals(0.1, rate.perMinute, 1e-9)
    }

    @Test
    fun `the overall rate pools cats and time, rather than averaging the outings' rates`() {
        // 2 cats over half an hour (4/h) and 10 over 18 minutes (33.3/h): pooled is 12 cats over
        // 48 minutes = 15/h, while the mean of the two rates would be 18.67/h.
        val slow = (0..1).map { at(NOON - 10.hours + (it * 30).minutes, "slow$it") }
        val fast = (0..9).map { at(NOON - 5.hours + (it * 2).minutes, "fast$it") }

        val rate = assertNotNull(stats(slow + fast).overallRate)

        assertEquals(15.0, rate.perHour, 1e-9)
    }

    @Test
    fun `the best outing is the fastest eligible one, not the biggest`() {
        val big = (0..9).map { at(NOON - 20.hours + (it * 30).minutes, "big$it") } // 10 cats in 4.5 h
        val fast = (0..2).map { at(NOON - 10.hours + (it * 5).minutes, "fast$it") } // 3 cats in 10 min

        val best = assertNotNull(stats(big + fast).bestOuting)

        assertEquals(3, best.session.count)
        assertTrue(best.rate.perHour > 10.0, "expected the fast outing, got ${best.rate.perHour}/h")
    }

    @Test
    fun `outings and active time cover every outing, including ones too short to rate`() {
        val first = listOf(at(NOON - 10.hours, "a"), at(NOON - 10.hours + 10.minutes, "b"))
        val second = listOf(at(NOON - 5.hours, "c"))

        val stats = stats(first + second)

        assertEquals(2, stats.outings)
        // The lone cat's outing adds nothing to the time but still counts as an outing.
        assertEquals(10.minutes, stats.activeTime)
    }

    @Test
    fun `the current outing is open while another cat would still join it`() {
        val start = NOW - 20.minutes
        val encounters = listOf(at(start, "a"), at(NOW - 1.minutes, "b"))

        val current = assertNotNull(stats(encounters).currentOuting)

        assertEquals(2, current.count)
        assertEquals(20.minutes, current.elapsed)
        assertNotNull(current.rate)
    }

    @Test
    fun `the current outing closes once the gap has passed`() {
        val lastSeen = NOW - Tuning.SESSION_GAP - 1.milliseconds

        assertNull(stats(listOf(at(lastSeen - 30.minutes, "a"), at(lastSeen, "b"))).currentOuting)
    }

    @Test
    fun `the current outing's elapsed time runs to now, not to its last cat`() {
        val current = assertNotNull(
            stats(listOf(at(NOW - 40.minutes, "a"), at(NOW - 20.minutes, "b"))).currentOuting,
        )

        assertEquals(40.minutes, current.elapsed)
    }

    @Test
    fun `a current outing too young to measure reports its count but no rate`() {
        val current = assertNotNull(
            stats(listOf(at(NOW - 2.minutes, "a"), at(NOW - 1.minutes, "b"))).currentOuting,
        )

        assertEquals(2, current.count)
        assertNull(current.rate)
    }

    @Test
    fun `a current outing exactly as old as the minimum has a live rate`() {
        val current = assertNotNull(
            stats(listOf(at(NOW - Tuning.MIN_RATE_DURATION, "a"), at(NOW, "b"))).currentOuting,
        )

        assertNotNull(current.rate)
    }

    @Test
    fun `a cat dated ahead of now has not been seen for a negative length of time`() {
        // A photo imported from a device whose clock runs fast, or any clock skew at all: the
        // encounter is real and belongs to the current outing, but it cannot have lasted -26 min.
        val current = assertNotNull(stats(listOf(at(NOW + 26.minutes, "ahead"))).currentOuting)

        assertEquals(1, current.count)
        assertEquals(Duration.ZERO, current.elapsed)
    }

    @Test
    fun `an outing that starts ahead of now reports no rate rather than an impossible one`() {
        val encounters = listOf(at(NOW + 26.minutes, "ahead"), at(NOW + 30.minutes, "later"))

        val current = assertNotNull(stats(encounters).currentOuting)

        // Zero elapsed is below the minimum duration, so the rate stays absent; a rate computed
        // over zero time would be infinite.
        assertNull(current.rate)
    }

    @Test
    fun `a cat dated ahead of now still leaves the stored durations non-negative`() {
        val stats = stats(listOf(at(NOW + 26.minutes, "ahead"), at(NOW + 30.minutes, "later")))

        assertTrue(stats.activeTime >= Duration.ZERO, "activeTime was ${stats.activeTime}")
        assertTrue(
            stats.bestOuting == null || stats.bestOuting.session.duration >= Duration.ZERO,
            "best outing duration was ${stats.bestOuting?.session?.duration}",
        )
    }

    private companion object {
        val NOW = Instant.parse("2026-09-22T18:00:00Z")
        val NOON = Instant.parse("2026-09-22T12:00:00Z")
        val TODAY = LocalDate(2026, 9, 22)
        val PLUS_TWO = UtcOffset(hours = 2)
        val MOSCOW = UtcOffset(hours = 3)
        val NEW_YORK = UtcOffset(hours = -5)
    }
}
