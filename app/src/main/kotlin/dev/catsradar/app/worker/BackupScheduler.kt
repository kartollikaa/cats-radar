package dev.catsradar.app.worker

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface BackupScheduler {
    fun export(target: String)
    fun import(source: String)

    /** The one backup run, if any; emits while it works and once more when it ends. */
    fun observe(): Flow<WorkInfo?>
}

class WorkManagerBackupScheduler(workManager: Lazy<WorkManager>) : BackupScheduler {
    private val workManager by workManager

    override fun export(target: String) {
        start(OneTimeWorkRequestBuilder<ExportBackupWorker>().setInputData(uriData(target)).build())
    }

    override fun import(source: String) {
        start(OneTimeWorkRequestBuilder<ImportBackupWorker>().setInputData(uriData(source)).build())
    }

    override fun observe(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME).map { it.lastOrNull() }

    // Export and import share one unique name so they cannot run at once: importing while an
    // export is still reading would archive a half-merged database.
    private fun start(request: OneTimeWorkRequest) {
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun uriData(value: String): Data =
        Data.Builder().putString(BackupWork.KEY_URI, value).build()

    private companion object {
        const val UNIQUE_NAME = "backup"
    }
}
