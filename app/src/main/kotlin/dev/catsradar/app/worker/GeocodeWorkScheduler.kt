package dev.catsradar.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Resolving a cell needs the network, so this is periodic and constrained rather than immediate:
 * a cat logged in a basement gets its place name whenever the phone is next online.
 */
object GeocodeWorkScheduler {

    const val UNIQUE_NAME = "geocode-pending-cells"

    private const val INTERVAL_HOURS = 6L
    private const val BACKOFF_MINUTES = 15L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<GeocodePendingCellsWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
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
}
