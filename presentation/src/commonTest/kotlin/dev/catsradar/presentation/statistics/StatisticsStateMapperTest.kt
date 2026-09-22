package dev.catsradar.presentation.statistics

import dev.catsradar.domain.model.Session
import dev.catsradar.domain.stats.Milestone
import dev.catsradar.domain.stats.Rate
import dev.catsradar.domain.stats.RatedOuting
import dev.catsradar.domain.stats.Stats
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class StatisticsStateMapperTest {

    private val mapper = StatisticsStateMapper(FakeDateTimeFormatter())

    private fun stats(
        total: Int = 0,
        overallRate: Rate? = null,
        bestOuting: RatedOuting? = null,
        nextMilestone: Milestone? = null,
        activeTime: Duration = Duration.ZERO,
    ) = Stats(
        total = total,
        today = 0,
        lastSevenDays = 0,
        lastThirtyDays = 0,
        withPhoto = 0,
        currentStreak = 0,
        longestStreak = 0,
        nextMilestone = nextMilestone,
        outings = 0,
        activeTime = activeTime,
        overallRate = overallRate,
        bestOuting = bestOuting,
        currentOuting = null,
    )

    @Test
    fun `no cats at all hides the numbers rather than showing a wall of zeroes`() {
        assertEquals(false, mapper.map(stats(total = 0)).hasAnyCats)
        assertEquals(true, mapper.map(stats(total = 1)).hasAnyCats)
    }

    @Test
    fun `a rate below one a minute reads per hour`() {
        val state = mapper.map(stats(total = 1, overallRate = Rate(perHour = 42.0)))

        assertEquals(RateState(value = "42.0", unit = RateUnit.PER_HOUR), state.overallRate)
    }

    @Test
    fun `a rate at or above one a minute switches to per minute`() {
        // 60/h is exactly one a minute: the boundary belongs to the minute side, or the hour label
        // would have to render "60.0 / h" for something the user experiences as one a minute.
        val atBoundary = mapper.map(stats(total = 1, overallRate = Rate(perHour = 60.0))).overallRate
        val above = mapper.map(stats(total = 1, overallRate = Rate(perHour = 120.0))).overallRate

        assertEquals(RateState(value = "1.0", unit = RateUnit.PER_MINUTE), atBoundary)
        assertEquals(RateState(value = "2.0", unit = RateUnit.PER_MINUTE), above)
    }

    @Test
    fun `a rate just under the boundary stays on hours`() {
        val state = mapper.map(stats(total = 1, overallRate = Rate(perHour = 59.9)))

        assertEquals(RateUnit.PER_HOUR, state.overallRate?.unit)
    }

    @Test
    fun `no measurable rate leaves the field empty for the screen to render a dash`() {
        assertNull(mapper.map(stats(total = 5, overallRate = null)).overallRate)
    }

    @Test
    fun `rates are rounded to one decimal rather than shown in full`() {
        val state = mapper.map(stats(total = 1, overallRate = Rate(perHour = 4.2666)))

        assertEquals("4.3", state.overallRate?.value)
    }

    @Test
    fun `the next milestone carries both the target and the distance to it`() {
        val state = mapper.map(stats(total = 147, nextMilestone = Milestone(value = 250, remaining = 103)))

        assertEquals(MilestoneState(valueLabel = "250", remainingLabel = "103"), state.nextMilestone)
    }

    @Test
    fun `the best outing carries its count, its duration and its own rate`() {
        val session = Session(
            count = 9,
            start = Instant.parse("2026-09-22T10:00:00Z"),
            end = Instant.parse("2026-09-22T10:42:00Z"),
            duration = 42.minutes,
        )
        val state = mapper.map(
            stats(total = 9, bestOuting = RatedOuting(session, Rate(perHour = 78.0))),
        )

        assertEquals(
            BestOutingState(
                countLabel = "9",
                durationLabel = 42.minutes.toString(),
                rate = RateState(value = "1.3", unit = RateUnit.PER_MINUTE),
            ),
            state.bestOuting,
        )
    }

    @Test
    fun `active time is formatted, not printed raw`() {
        val state = mapper.map(stats(total = 1, activeTime = 3.hours))

        assertEquals(3.hours.toString(), state.activeTimeLabel)
    }
}
