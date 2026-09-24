package dev.catsradar.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.catsradar.app.reporting.NonFatalReporter
import dev.catsradar.domain.usecase.AttachLocation
import kotlinx.coroutines.CancellationException

class AttachLocationWorker(
    context: Context,
    params: WorkerParameters,
    private val attachLocation: AttachLocation,
    private val reporter: NonFatalReporter,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught") // an unexpected failure must not crash the process
    override suspend fun doWork(): Result {
        val encounterId = inputData.getString(KEY_ENCOUNTER_ID) ?: return Result.failure()
        return try {
            attachLocation(encounterId)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            reporter.record(e)
            Result.failure()
        }
    }

    companion object {
        const val KEY_ENCOUNTER_ID = "encounter_id"
    }
}
