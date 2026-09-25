package dev.catsradar.app.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Housekeeping, so it waits for a moment the user is not using the phone. */
class PurgeWorkScheduler(private val workManager: WorkManager) {

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<PurgeDeletedWorker>(INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build())
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            // KEEP, or every launch would restart the period and the purge could never come due.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val UNIQUE_NAME = "purge-deleted"
        const val INTERVAL_DAYS = 1L
    }
}
