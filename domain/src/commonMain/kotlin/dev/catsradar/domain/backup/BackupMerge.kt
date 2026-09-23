package dev.catsradar.domain.backup

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus

/**
 * Reconciles an imported backup against what is already here. Pure: it decides, it does not write.
 *
 * There is no server to arbitrate, so the rules below settle every conflict from the two rows alone.
 */
object BackupMerge {

    fun merge(local: BackupContents, imported: BackupContents): MergeResult {
        val localById = local.encounters.associateBy { it.id }
        var added = 0
        var updated = 0
        var unchanged = 0
        val encounters = mutableListOf<Encounter>()

        imported.encounters.forEach { candidate ->
            val existing = localById[candidate.id]
            when {
                existing == null -> {
                    encounters += candidate
                    added++
                }
                importedWins(local = existing, imported = candidate) -> {
                    encounters += candidate
                    updated++
                }
                else -> unchanged++
            }
        }

        val localCells = local.placeCells.associateBy { it.cellId }
        val placeCells = imported.placeCells.bestPerCell().filter { candidate ->
            val existing = localCells[candidate.cellId]
            existing == null || replaces(offered = candidate, kept = existing)
        }

        return MergeResult(
            encounters = encounters,
            placeCells = placeCells,
            added = added,
            updated = updated,
            unchanged = unchanged,
        )
    }

    /**
     * A deletion outranks a live row it post-dates, so an old backup cannot resurrect a cat the user
     * removed after taking it. Otherwise the later edit wins, and a tie keeps what is already here —
     * which is what makes importing a backup of the current state write nothing at all.
     */
    private fun importedWins(local: Encounter, imported: Encounter): Boolean {
        // Only the local row can be deleted: an export carries live rows only, so a tombstone
        // never travels in an archive.
        val deletedAt = local.deletedAt
        return if (deletedAt != null) {
            deletedAt <= imported.updatedAt
        } else {
            imported.updatedAt > local.updatedAt
        }
    }

    /**
     * A name beats no name: a cell someone's device managed to resolve is worth more than one that
     * is still pending or gave up, however many attempts went into it.
     */
    private fun replaces(offered: PlaceCell, kept: PlaceCell): Boolean {
        val keptResolved = kept.status == PlaceStatus.RESOLVED
        val offeredResolved = offered.status == PlaceStatus.RESOLVED
        return when {
            offeredResolved && !keptResolved -> true
            !offeredResolved -> false
            else -> isFresherResolution(offered = offered, kept = kept)
        }
    }

    private fun isFresherResolution(offered: PlaceCell, kept: PlaceCell): Boolean {
        val keptAt = kept.resolvedAt
        val offeredAt = offered.resolvedAt ?: return false
        return keptAt == null || offeredAt > keptAt
    }

    // An archive is a file, not a table: nothing stops it listing one cell twice.
    private fun List<PlaceCell>.bestPerCell(): List<PlaceCell> =
        groupBy { it.cellId }.values.map { rows ->
            rows.reduce { kept, offered -> if (replaces(offered = offered, kept = kept)) offered else kept }
        }
}
