package dev.catsradar.domain.usecase

import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.backup.BackupMerge
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupReader
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.first

sealed interface ImportBackupResult {
    data class Merged(val added: Int, val updated: Int, val unchanged: Int) : ImportBackupResult

    /** Nothing was written; the archive never got as far as being merged. */
    data class Rejected(val reason: BackupRejection) : ImportBackupResult
}

class ImportBackup(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
    private val backupReader: BackupReader,
) {
    suspend operator fun invoke(source: String): ImportBackupResult =
        when (val read = backupReader.read(source)) {
            is BackupReadResult.Rejected -> ImportBackupResult.Rejected(read.reason)
            is BackupReadResult.Readable -> write(read.contents)
        }

    private suspend fun write(imported: BackupContents): ImportBackupResult {
        // loadEvery, not observeAll: a cat deleted here must stay deleted when an older backup
        // offers it back, and only the deleted row itself carries the deletedAt that decides.
        val localEncounters = encounterRepository.loadEvery()
        val local = BackupContents(
            encounters = localEncounters,
            placeCells = placeCellRepository.observeAll().first(),
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

        return ImportBackupResult.Merged(
            added = merged.added,
            updated = merged.updated,
            unchanged = merged.unchanged,
        )
    }
}
