package dev.catsradar.domain.stats

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.Session
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.domain.time.localDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Duration
import kotlin.time.Instant

private const val WEEK_DAYS = 7
private const val MONTH_DAYS = 30
private const val SECONDS_PER_HOUR = 3600.0

object StatsCalculator {

    fun calculate(
        encounters: List<Encounter>,
        today: LocalDate,
        now: Instant,
        gap: Duration = Tuning.SESSION_GAP,
        minRateDuration: Duration = Tuning.MIN_RATE_DURATION,
    ): Stats {
        val live = encounters.filter { it.deletedAt == null }
        val days = live.map { it.localDate() }
        val outings = SessionSplitter.groupByOuting(live, gap)
        val sessions = SessionSplitter.split(live, gap)
        val eligible = sessions.filter { it.isRateEligible(minRateDuration) }

        return Stats(
            total = live.size,
            today = days.count { it == today },
            lastSevenDays = days.countWithin(today, WEEK_DAYS),
            lastThirtyDays = days.countWithin(today, MONTH_DAYS),
            withPhoto = live.count { it.kind == EncounterKind.PHOTO },
            currentStreak = Streaks.current(days.toSet(), today),
            longestStreak = Streaks.longest(days.toSet()),
            nextMilestone = nextMilestone(live.size),
            outings = sessions.size,
            activeTime = sessions.fold(Duration.ZERO) { total, session -> total + session.duration },
            overallRate = overallRate(eligible),
            bestOuting = eligible.maxByOrNull { it.rate().perHour }?.let { RatedOuting(it, it.rate()) },
            currentOuting = currentOuting(outings.lastOrNull(), now, gap, minRateDuration),
        )
    }

    private fun Session.isRateEligible(minRateDuration: Duration): Boolean =
        count >= MIN_RATE_COUNT && duration >= minRateDuration

    private fun Session.rate(): Rate = Rate(count / duration.hours())

    private fun Duration.hours(): Double = inWholeMilliseconds / MILLIS_PER_HOUR

    // Counting the day itself, so "7 days" is today plus the six before it, not today plus seven.
    private fun List<LocalDate>.countWithin(today: LocalDate, days: Int): Int {
        val earliest = today.minus(DatePeriod(days = days - 1))
        return count { it >= earliest && it <= today }
    }

    private fun nextMilestone(total: Int): Milestone? =
        Tuning.MILESTONES.firstOrNull { it > total }?.let { Milestone(value = it, remaining = it - total) }

    private fun overallRate(eligible: List<Session>): Rate? {
        if (eligible.isEmpty()) return null
        val cats = eligible.sumOf { it.count }
        val hours = eligible.fold(Duration.ZERO) { total, session -> total + session.duration }.hours()
        return Rate(cats / hours)
    }

    private fun currentOuting(
        latest: List<Encounter>?,
        now: Instant,
        gap: Duration,
        minRateDuration: Duration,
    ): CurrentOuting? {
        // Open only while another cat would still join it; past the gap the outing is history.
        val open = latest?.takeIf { it.isNotEmpty() && now - it.last().occurredAt <= gap } ?: return null

        val elapsed = now - open.first().occurredAt
        val eligible = open.size >= MIN_RATE_COUNT && elapsed >= minRateDuration
        return CurrentOuting(
            count = open.size,
            elapsed = elapsed,
            rate = if (eligible) Rate(open.size / elapsed.hours()) else null,
        )
    }

    private const val MIN_RATE_COUNT = 2
    private const val MILLIS_PER_HOUR = SECONDS_PER_HOUR * 1000
}
