package dev.catsradar.app.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** A new cell gets a one-time pass as soon as the phone is online; a failed one waits for the periodic retry. */
class GeocodeWorkScheduler(private val workManager: WorkManager) : PlaceNamingScheduler {

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<GeocodePendingCellsWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            // KEEP: re-enqueuing on every launch would reset the period and the backoff, so a cell
            // that keeps failing would be retried far more often than intended.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun unschedule() {
        workManager.cancelUniqueWork(UNIQUE_NAME)
    }

    override fun nameUntriedCells() {
        val request = OneTimeWorkRequestBuilder<GeocodePendingCellsWorker>()
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putBoolean(GeocodePendingCellsWorker.KEY_UNTRIED_ONLY, true).build())
            .build()

        workManager.enqueueUniqueWork(
            UNTRIED_UNIQUE_NAME,
            // Not KEEP: a pass already running may be past the new cell. Starting over costs at most
            // one lookup, because the pass only touches cells nobody has looked up.
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        private const val UNIQUE_NAME = "geocode-pending-cells"
        const val UNTRIED_UNIQUE_NAME = "geocode-untried-cells"

        private const val INTERVAL_HOURS = 6L
        private const val BACKOFF_MINUTES = 15L

        private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
