package dev.catsradar.domain.session

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.Session
import kotlin.time.Duration

/** Groups non-deleted encounters into outings: a gap strictly greater than [gap] starts a new session. */
object SessionSplitter {
    fun split(encounters: List<Encounter>, gap: Duration = Tuning.SESSION_GAP): List<Session> =
        groupByOuting(encounters, gap).map { outing ->
            Session(
                count = outing.size,
                start = outing.first().occurredAt,
                end = outing.last().occurredAt,
                duration = outing.last().occurredAt - outing.first().occurredAt,
            )
        }

    /**
     * The same outing boundary as [split], returning each outing's own encounters (oldest first)
     * instead of aggregate counts - the one place that boundary is computed; [split] is defined in
     * terms of it rather than re-deriving the gap comparison.
     */
    fun groupByOuting(encounters: List<Encounter>, gap: Duration = Tuning.SESSION_GAP): List<List<Encounter>> {
        val sorted = encounters.filter { it.deletedAt == null }.sortedBy { it.occurredAt }
        if (sorted.isEmpty()) return emptyList()

        val outings = mutableListOf<MutableList<Encounter>>()
        var current = mutableListOf(sorted.first())
        for (index in 1 until sorted.size) {
            val encounter = sorted[index]
            if (encounter.occurredAt - current.last().occurredAt > gap) {
                outings += current
                current = mutableListOf()
            }
            current += encounter
        }
        outings += current
        return outings
    }
}
