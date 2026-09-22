package dev.catsradar.app.worker

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class WorkManagerLocationAttachScheduler(context: Context) : LocationAttachScheduler {
    private val appContext = context.applicationContext

    // Lazy: WorkManager.initialize() runs in CatsRadarApplication.onCreate(), after Koin starts;
    // resolving this eagerly for DI-graph checks must not require WorkManager already running.
    private val workManager by lazy { WorkManager.getInstance(appContext) }

    override fun schedule(encounterId: String) {
        val request = OneTimeWorkRequestBuilder<AttachLocationWorker>()
            .setInputData(Data.Builder().putString(AttachLocationWorker.KEY_ENCOUNTER_ID, encounterId).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueue(request)
    }
}
