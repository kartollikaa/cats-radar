package dev.catsradar.domain.usecase

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.backup.BackupMerge
import dev.catsradar.domain.backup.asImported
import dev.catsradar.domain.backup.withLocationFromCoordinates
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupReader
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.region.PlaceCells
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.repository.TransactionRunner
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.flow.first

sealed interface ImportBackupResult {
    data class Merged(val added: Int, val updated: Int, val unchanged: Int) : ImportBackupResult

    /** No row was written; the archive never got as far as being merged. */
    data class Rejected(val reason: BackupRejection) : ImportBackupResult
}

class ImportBackup(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
    private val walkRepository: WalkRepository,
    private val transactionRunner: TransactionRunner,
    private val backupReader: BackupReader,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(source: String): ImportBackupResult {
        val result = when (val read = backupReader.read(source)) {
            is BackupReadResult.Rejected -> ImportBackupResult.Rejected(read.reason)
            // The merge's reads go inside too: deciding on rows another writer then changes would undo that change.
            is BackupReadResult.Readable -> transactionRunner.inTransaction { write(read.contents) }
        }
        analytics.log(result.event())
        return result
    }

    private fun ImportBackupResult.event(): AnalyticsEvent = when (this) {
        is ImportBackupResult.Merged -> AnalyticsEvent.BackupImported(added, updated, unchanged)
        is ImportBackupResult.Rejected -> AnalyticsEvent.BackupRejected(reason)
    }

    private suspend fun write(archived: BackupContents): ImportBackupResult {
        val imported = archived.copy(
            encounters = archived.encounters.map { it.withLocationFromCoordinates() },
            placeCells = archived.placeCells.mapNotNull { it.asImported() },
        )
        // loadEvery, not observeAll: a cat deleted here must stay deleted when an older backup
        // offers it back, and only the deleted row itself carries the deletedAt that decides.
        val localEncounters = encounterRepository.loadEvery()
        val local = BackupContents(
            encounters = localEncounters,
            placeCells = placeCellRepository.observeAll().first(),
            walks = walkRepository.observeAll().first(),
            trackPoints = walkRepository.loadEveryPoint(),
        )
        val merged = BackupMerge.merge(local = local, imported = imported)

        val known = localEncounters.mapTo(mutableSetOf()) { it.id }
        merged.encounters.forEach { encounter ->
            if (encounter.id in known) {
                encounterRepository.update(encounter)
            } else {
                encounterRepository.insert(encounter)
            }
        }
        merged.placeCells.forEach { placeCellRepository.upsert(it) }
        merged.encounters.mapNotNullTo(mutableSetOf()) { it.geohash }
            .forEach { PlaceCells.remember(placeCellRepository, it) }
        // Walks first: a point may belong to a walk this import is adding.
        merged.walks.forEach { walkRepository.upsert(it) }
        walkRepository.appendPoints(merged.trackPoints)

        return ImportBackupResult.Merged(
            added = merged.added,
            updated = merged.updated,
            unchanged = merged.unchanged,
        )
    }
}
