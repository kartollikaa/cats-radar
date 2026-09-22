package dev.catsradar.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dev.catsradar.domain.platform.BackupRejection
import dev.catsradar.domain.usecase.ExportBackup
import dev.catsradar.domain.usecase.ImportBackup
import dev.catsradar.domain.usecase.ImportBackupResult
import kotlinx.coroutines.CancellationException

internal object BackupWork {
    const val KEY_URI = "uri"
    const val KEY_ADDED = "added"
    const val KEY_UPDATED = "updated"
    const val KEY_REJECTION = "rejection"
}

class ExportBackupWorker(
    context: Context,
    params: WorkerParameters,
    private val exportBackup: ExportBackup,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // an unexpected failure must not crash
    override suspend fun doWork(): Result {
        val target = inputData.getString(BackupWork.KEY_URI) ?: return Result.failure()
        return try {
            // Never Result.retry(): the picker's write grant dies with the process, so a retry
            // would write nothing and report success for an archive that does not exist.
            if (exportBackup(target)) Result.success() else Result.failure()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure()
        }
    }
}

class ImportBackupWorker(
    context: Context,
    params: WorkerParameters,
    private val importBackup: ImportBackup,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // an unexpected failure must not crash
    override suspend fun doWork(): Result {
        val source = inputData.getString(BackupWork.KEY_URI) ?: return Result.failure()
        return try {
            when (val result = importBackup(source)) {
                is ImportBackupResult.Merged -> Result.success(result.toData())
                is ImportBackupResult.Rejected -> Result.failure(result.reason.toData())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun ImportBackupResult.Merged.toData(): Data = Data.Builder()
        .putInt(BackupWork.KEY_ADDED, added)
        .putInt(BackupWork.KEY_UPDATED, updated)
        .build()

    private fun BackupRejection.toData(): Data = Data.Builder()
        .putString(BackupWork.KEY_REJECTION, name)
        .build()
}
