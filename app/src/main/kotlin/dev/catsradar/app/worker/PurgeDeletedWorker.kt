package dev.catsradar.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.catsradar.app.reporting.NonFatalReporter
import dev.catsradar.domain.usecase.PurgeDeleted
import kotlinx.coroutines.CancellationException

class PurgeDeletedWorker(
    context: Context,
    params: WorkerParameters,
    private val purgeDeleted: PurgeDeleted,
    private val reporter: NonFatalReporter,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught") // an unexpected failure must not crash the process
    override suspend fun doWork(): Result = try {
        purgeDeleted()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        recordOnFirstAttempt(reporter, e)
        Result.retry()
    }
}
