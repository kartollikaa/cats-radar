package dev.catsradar.app.worker

import android.content.Context
import android.os.Build
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

    private val batches = ImportBatches(appContext)

    override fun start(uris: List<String>) {
        val requestBuilder = OneTimeWorkRequestBuilder<ImportPhotosWorker>()
        if (shouldExpedite(sdkInt)) {
            requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val request = requestBuilder.build()
        // Not input data: WorkManager caps it at Data.MAX_DATA_BYTES, which a batch of long URIs outgrows.
        batches.replaceWith(request.id, uris)
        // REPLACE, not KEEP: the user picking a second batch means they want that batch.
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun observe(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME).map { it.lastOrNull() }

    private companion object {
        const val UNIQUE_NAME = "import-photos"
    }
}
