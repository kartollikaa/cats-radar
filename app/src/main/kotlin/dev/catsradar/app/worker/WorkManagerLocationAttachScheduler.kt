package dev.catsradar.app.worker

import android.content.Context
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class WorkManagerLocationAttachScheduler(
    context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : LocationAttachScheduler {
    private val appContext = context.applicationContext

    // Lazy: WorkManager.initialize() runs in CatsRadarApplication.onCreate(), after Koin starts;
    // resolving this eagerly for DI-graph checks must not require WorkManager already running.
    private val workManager by lazy { WorkManager.getInstance(appContext) }

    // Named by encounter id so undo can cancel it by that same name. Cancellation is best-effort
    // (the worker may already be mid-flight); EncounterDao.attachLocation's own deletedAt guard
    // is what makes that race safe, not this call - cancelling here only saves the wasted fetch.
    override fun schedule(encounterId: String) {
        val requestBuilder = OneTimeWorkRequestBuilder<AttachLocationWorker>()
            .setInputData(Data.Builder().putString(AttachLocationWorker.KEY_ENCOUNTER_ID, encounterId).build())
        if (shouldExpedite(sdkInt)) {
            requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        workManager.enqueueUniqueWork(encounterId, ExistingWorkPolicy.KEEP, requestBuilder.build())
    }

    override fun cancel(encounterId: String) {
        workManager.cancelUniqueWork(encounterId)
    }
}

// Below API 31, expedited work always takes the legacy foreground-service path regardless of
// OutOfQuotaPolicy, and CoroutineWorker's default getForegroundInfo() throws - so this worker
// must run as ordinary work there instead of crashing on every tap.
internal fun shouldExpedite(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S
