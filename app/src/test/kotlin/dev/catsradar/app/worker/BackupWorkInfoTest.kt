package dev.catsradar.app.worker

import androidx.work.Data
import androidx.work.WorkInfo
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.presentation.settings.BackupOutcome
import dev.catsradar.presentation.settings.SettingsIntent
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val RunId = UUID.fromString("6f1c2b9e-4d7a-4e5b-9c3d-2a8f0e1b7c64")

private fun workInfo(
    state: WorkInfo.State,
    worker: Class<*>,
    output: Data = Data.EMPTY,
): WorkInfo = WorkInfo(
    id = RunId,
    state = state,
    tags = setOf(worker.name),
    outputData = output,
)

private fun rejection(reason: BackupRejection): Data =
    Data.Builder().putString(BackupWork.KEY_REJECTION, reason.name).build()

class BackupWorkInfoTest {

    @Test
    fun `a running backup tells the screen to show itself busy`() {
        val intent = workInfo(WorkInfo.State.RUNNING, ExportBackupWorker::class.java).toSettingsIntent()

        assertEquals(SettingsIntent.Backup.Started, intent)
    }

    @Test
    fun `a finished export reports an export, not an import`() {
        val intent = workInfo(WorkInfo.State.SUCCEEDED, ExportBackupWorker::class.java).toSettingsIntent()

        assertEquals(SettingsIntent.Backup.Finished(RunId.toString(), BackupOutcome.EXPORTED), intent)
    }

    @Test
    fun `a finished import reports an import`() {
        val intent = workInfo(WorkInfo.State.SUCCEEDED, ImportBackupWorker::class.java).toSettingsIntent()

        assertEquals(SettingsIntent.Backup.Finished(RunId.toString(), BackupOutcome.IMPORTED), intent)
    }

    @Test
    fun `a failed export never reports an import problem`() {
        val intent = workInfo(WorkInfo.State.FAILED, ExportBackupWorker::class.java).toSettingsIntent()

        assertEquals(SettingsIntent.Backup.Finished(RunId.toString(), BackupOutcome.EXPORT_FAILED), intent)
    }

    @Test
    fun `an archive from a newer version says so rather than blaming the file`() {
        val intent = workInfo(
            WorkInfo.State.FAILED,
            ImportBackupWorker::class.java,
            output = rejection(BackupRejection.TOO_NEW),
        ).toSettingsIntent()

        assertEquals(SettingsIntent.Backup.Finished(RunId.toString(), BackupOutcome.IMPORT_REFUSED_TOO_NEW), intent)
    }

    @Test
    fun `an import that failed without a reason reads as an unreadable file`() {
        val intent = workInfo(WorkInfo.State.FAILED, ImportBackupWorker::class.java).toSettingsIntent()

        assertEquals(SettingsIntent.Backup.Finished(RunId.toString(), BackupOutcome.IMPORT_REFUSED_UNREADABLE), intent)
    }

    @Test
    fun `a cancelled run still clears the progress bar`() {
        val intent = workInfo(WorkInfo.State.CANCELLED, ExportBackupWorker::class.java).toSettingsIntent()

        assertEquals(SettingsIntent.Backup.Finished(RunId.toString(), BackupOutcome.EXPORT_FAILED), intent)
    }

    @Test
    fun `work that has not started yet says nothing`() {
        assertNull(workInfo(WorkInfo.State.ENQUEUED, ExportBackupWorker::class.java).toSettingsIntent())
        assertNull(workInfo(WorkInfo.State.BLOCKED, ImportBackupWorker::class.java).toSettingsIntent())
    }
}
