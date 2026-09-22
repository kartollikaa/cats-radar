package dev.catsradar.app.worker

import android.content.Context
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ImportScheduler {
    fun start(uris: List<String>)

    /** The one import run, if any; emits while it works and once more when it ends. */
    fun observe(): Flow<WorkInfo?>
}

class WorkManagerImportScheduler(
    context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : ImportScheduler {
    private val appContext = context.applicationContext

    // Lazy for the same reason as the location scheduler: WorkManager.initialize() runs after Koin.
    private val workManager by lazy { WorkManager.getInstance(appContext) }

    override fun start(uris: List<String>) {
        val requestBuilder = OneTimeWorkRequestBuilder<ImportPhotosWorker>()
            .setInputData(
                Data.Builder().putStringArray(ImportPhotosWorker.KEY_URIS, uris.toTypedArray()).build(),
            )
        if (shouldExpedite(sdkInt)) {
            requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        // REPLACE, not KEEP: the user picking a second batch means they want that batch, and the
        // picker's read grants are per-process anyway, so a queued older run has nothing to lose.
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, requestBuilder.build())
    }

    override fun observe(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME).map { it.lastOrNull() }

    private companion object {
        const val UNIQUE_NAME = "import-photos"
    }
}
