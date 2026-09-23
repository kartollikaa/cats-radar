package dev.catsradar.domain.usecase

import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.platform.BackupWriter
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.flow.first

class ExportBackup(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
    private val walkRepository: WalkRepository,
    private val backupWriter: BackupWriter,
) {
    /** False when the archive could not be written. */
    suspend operator fun invoke(target: String): Boolean {
        // Soft-deleted rows stay home: a backup is what the user has, not what they threw away,
        // and carrying tombstones would resurrect them as deletions on a device that never saw them.
        val contents = BackupContents(
            encounters = encounterRepository.observeAll().first(),
            placeCells = placeCellRepository.observeAll().first(),
            walks = walkRepository.observeAll().first(),
            trackPoints = walkRepository.loadEveryPoint(),
        )
        return backupWriter.write(target, contents)
    }
}
