package dev.catsradar.domain.session

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.Session
import kotlin.time.Duration

/** Groups encounters into outings (spec §3.5): a gap strictly greater than [gap] starts a new one. */
object SessionSplitter {
    fun split(encounters: List<Encounter>, gap: Duration = Tuning.SESSION_GAP): List<Session> {
        if (encounters.isEmpty()) return emptyList()
        val sorted = encounters.sortedBy { it.occurredAt }

        val sessions = mutableListOf<Session>()
        var sessionStart = sorted.first().occurredAt
        var sessionEnd = sessionStart
        var count = 1

        for (index in 1 until sorted.size) {
            val occurredAt = sorted[index].occurredAt
            if (occurredAt - sessionEnd > gap) {
                sessions += Session(count, sessionStart, sessionEnd, sessionEnd - sessionStart)
                sessionStart = occurredAt
                count = 0
            }
            sessionEnd = occurredAt
            count++
        }
        sessions += Session(count, sessionStart, sessionEnd, sessionEnd - sessionStart)
        return sessions
    }
}
