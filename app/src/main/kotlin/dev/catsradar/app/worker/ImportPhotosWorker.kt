package dev.catsradar.app.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dev.catsradar.app.notification.ImportNotifier
import dev.catsradar.app.photo.releaseReadAccess
import dev.catsradar.domain.usecase.ImportSummary
import kotlinx.coroutines.CancellationException

typealias PhotoImport =
    suspend (sourceUris: List<String>, onProgress: (done: Int, total: Int) -> Unit) -> ImportSummary

class ImportPhotosWorker(
    context: Context,
    params: WorkerParameters,
    private val importPhotos: PhotoImport,
    private val notifier: ImportNotifier,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // an unexpected failure must not crash the process
    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(KEY_URIS)?.toList().orEmpty()
        if (uris.isEmpty()) return Result.success(summaryOf(emptyList(), skipped = 0, failed = 0))

        return try {
            notifier.showProgress(done = 0, total = uris.size)
            val summary = importPhotos(uris) { done, total ->
                setProgressAsync(
                    Data.Builder().putInt(KEY_DONE, done).putInt(KEY_TOTAL, total).build(),
                )
                notifier.showProgress(done = done, total = total)
            }
            Result.success(summaryOf(summary.added.map { it.id }, summary.skipped, summary.failed))
        } catch (e: CancellationException) {
            // Stopped, not finished: WorkManager may run this batch again, so its photos stay held.
            throw e
        } catch (e: Exception) {
            // Never Result.retry(): not every source's read grant outlives the process, and a retry
            // without one would import nothing and report every photo as failed.
            Result.failure()
        } finally {
            // Whatever happened, the running commentary stops: an ongoing notification left behind
            // is one the user cannot dismiss.
            notifier.clear()
        }.also { applicationContext.contentResolver.releaseReadAccess(uris.map(Uri::parse)) }
    }

    private fun summaryOf(addedIds: List<String>, skipped: Int, failed: Int): Data = Data.Builder()
        .putStringArray(KEY_ADDED_IDS, addedIds.toTypedArray())
        .putInt(KEY_SKIPPED, skipped)
        .putInt(KEY_FAILED, failed)
        .build()

    companion object {
        const val KEY_URIS = "uris"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ADDED_IDS = "addedIds"
        const val KEY_SKIPPED = "skipped"
        const val KEY_FAILED = "failed"
    }
}
