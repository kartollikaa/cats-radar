package dev.catsradar.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.catsradar.domain.usecase.PurgeDeleted
import kotlinx.coroutines.CancellationException

class PurgeDeletedWorker(
    context: Context,
    params: WorkerParameters,
    private val purgeDeleted: PurgeDeleted,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // an unexpected failure must not crash the process
    override suspend fun doWork(): Result = try {
        purgeDeleted()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}
