package dev.catsradar.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.catsradar.domain.usecase.ResolvePendingPlaces
import kotlinx.coroutines.CancellationException

class GeocodePendingCellsWorker(
    private val context: Context,
    params: WorkerParameters,
    private val resolvePendingPlaces: ResolvePendingPlaces,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // an unexpected failure must not crash the process
    override suspend fun doWork(): Result = try {
        val untriedOnly = inputData.getBoolean(KEY_UNTRIED_ONLY, false)
        val geocoderPresent = if (untriedOnly) resolvePendingPlaces.resolveUntried() else resolvePendingPlaces()
        if (!geocoderPresent) {
            // No geocoder on this device, and there never will be: waking up on a schedule to
            // find that out again would cost battery for nothing.
            WorkManager.getInstance(context).cancelUniqueWork(GeocodeWorkScheduler.UNIQUE_NAME)
        }
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val KEY_UNTRIED_ONLY = "untried_only"
    }
}
