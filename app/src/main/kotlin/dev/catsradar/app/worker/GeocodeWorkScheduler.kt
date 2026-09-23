package dev.catsradar.app.worker

import android.content.Context
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
object GeocodeWorkScheduler {

    const val UNIQUE_NAME = "geocode-pending-cells"
    const val UNTRIED_UNIQUE_NAME = "geocode-untried-cells"

    private const val INTERVAL_HOURS = 6L
    private const val BACKOFF_MINUTES = 15L

    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<GeocodePendingCellsWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            // KEEP: re-enqueuing on every launch would reset the period and the backoff, so a cell
            // that keeps failing would be retried far more often than intended.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun nameUntriedCells(context: Context) {
        val request = OneTimeWorkRequestBuilder<GeocodePendingCellsWorker>()
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putBoolean(GeocodePendingCellsWorker.KEY_UNTRIED_ONLY, true).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNTRIED_UNIQUE_NAME,
            // Not KEEP: a pass already running may be past the new cell. An extra pass costs nothing,
            // because it only touches cells nobody has looked up.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
