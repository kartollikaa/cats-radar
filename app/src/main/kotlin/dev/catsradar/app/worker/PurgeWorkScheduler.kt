package dev.catsradar.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Housekeeping, so it waits for a moment the user is not using the phone. */
object PurgeWorkScheduler {

    private const val UNIQUE_NAME = "purge-deleted"
    private const val INTERVAL_DAYS = 1L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PurgeDeletedWorker>(INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            // KEEP, or every launch would restart the period and the purge could never come due.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
