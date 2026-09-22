package dev.catsradar.app.worker

import androidx.work.WorkInfo
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.presentation.settings.BackupOutcome
import dev.catsradar.presentation.settings.SettingsIntent

/**
 * What the Settings screen should be told about the one backup run, or null when nothing has
 * changed for it. Which worker ran is read back from its tags, so an outcome never says "exported"
 * about an import.
 */
internal fun WorkInfo.toSettingsIntent(): SettingsIntent.Backup? = when (state) {
    WorkInfo.State.RUNNING -> SettingsIntent.Backup.Started
    WorkInfo.State.SUCCEEDED -> SettingsIntent.Backup.Finished(succeededOutcome())
    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> SettingsIntent.Backup.Finished(failedOutcome())
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> null
}

private fun WorkInfo.isImport(): Boolean = tags.contains(ImportBackupWorker::class.java.name)

private fun WorkInfo.succeededOutcome(): BackupOutcome =
    if (isImport()) BackupOutcome.IMPORTED else BackupOutcome.EXPORTED

private fun WorkInfo.failedOutcome(): BackupOutcome = when {
    !isImport() -> BackupOutcome.EXPORT_FAILED
    outputData.getString(BackupWork.KEY_REJECTION) == BackupRejection.TOO_NEW.name ->
        BackupOutcome.IMPORT_REFUSED_TOO_NEW
    // A cancelled import and a corrupt one both land here, and both mean the same to the user:
    // nothing was written and the archive is not one this app can read.
    else -> BackupOutcome.IMPORT_REFUSED_UNREADABLE
}
