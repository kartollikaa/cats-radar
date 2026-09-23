package dev.catsradar.domain.backup

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import kotlin.time.Instant

/**
 * Walks merge by `id` as cats do: the later edit wins, and a tie keeps the walk here. A route is the
 * union of both copies' points, a point being one walk at one moment, so no import shortens one.
 */
internal object WalkMerge {

    fun merge(local: BackupContents, imported: BackupContents): Pair<List<Walk>, List<TrackPoint>> {
        val localWalks = local.walks.associateBy { it.id }
        val known = localWalks.keys + imported.walks.map { it.id }
        val here = local.trackPoints.mapTo(mutableSetOf()) { it.walkId to it.at }
        val newPoints = imported.trackPoints
            .distinctBy { it.walkId to it.at }
            .filter { (it.walkId to it.at) !in here && it.walkId in known }
        val lastPointAt = (local.trackPoints + newPoints)
            .groupBy { it.walkId }
            .mapValues { (_, points) -> points.maxOf { it.at } }
        val onHere = local.walks.firstOrNull { it.endedAt == null }?.id

        val walks = imported.walks.mapNotNull { candidate ->
            val existing = localWalks[candidate.id]
            val winner = if (existing == null || candidate.updatedAt > existing.updatedAt) candidate else existing
            val settled = winner.settle(isOnHere = winner.id == onHere, lastPointAt = lastPointAt[winner.id])
            settled.takeIf { it != existing }
        }
        return walks to newPoints
    }

    // A walk still on in an archive was being recorded on another phone and cannot go on here, so it
    // arrives ended; and a walk ended that way reaches the last point any later archive brings.
    private fun Walk.settle(isOnHere: Boolean, lastPointAt: Instant?): Walk {
        val end = endedAt
        return when {
            end == null && !isOnHere -> copy(endedAt = lastPointAt ?: startedAt)
            end != null && lastPointAt != null && lastPointAt > end -> copy(endedAt = lastPointAt)
            else -> this
        }
    }
}
