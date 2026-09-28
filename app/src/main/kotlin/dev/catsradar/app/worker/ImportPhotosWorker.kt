package dev.catsradar.app.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dev.catsradar.app.notification.ImportNotifier
import dev.catsradar.app.photo.releaseReadAccess
import dev.catsradar.app.reporting.NonFatalReporter
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.usecase.ImportSummary
import kotlinx.coroutines.CancellationException

typealias PhotoImport =
    suspend (sourceUris: List<String>, onProgress: (done: Int, total: Int) -> Unit) -> ImportSummary

class ImportPhotosWorker internal constructor(
    context: Context,
    params: WorkerParameters,
    private val importPhotos: PhotoImport,
    private val batches: ImportBatches,
    private val notifier: ImportNotifier,
    private val reporter: NonFatalReporter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uris = batches.read(id)
        return import(uris).also {
            applicationContext.contentResolver.releaseReadAccess(uris.map(Uri::parse))
            batches.delete(id)
        }
    }

    @Suppress("TooGenericExceptionCaught") // an unexpected failure must not crash the process
    private suspend fun import(uris: List<String>): Result {
        if (uris.isEmpty()) return Result.success(summaryOf(emptyList(), skipped = 0, failed = 0))

        val previews = previewsThatFit(uris.take(Tuning.IMPORT_PREVIEWS))
        return try {
            notifier.showProgress(done = 0, total = uris.size)
            val summary = importPhotos(uris) { done, total ->
                setProgressAsync(progressOf(done, total, previews))
                notifier.showProgress(done = done, total = total)
            }
            Result.success(summaryOf(summary.added.map { it.id }, summary.skipped, summary.failed))
        } catch (e: CancellationException) {
            // Stopped, not finished: WorkManager may run this batch again, so its photos stay held.
            throw e
        } catch (e: Throwable) {
            // Never Result.retry(): not every source's read grant outlives the process, and a retry
            // without one would import nothing and report every photo as failed.
            reporter.record(e)
            Result.failure()
        } finally {
            // Whatever happened, the running commentary stops: an ongoing notification left behind
            // is one the user cannot dismiss.
            notifier.clear()
        }
    }

    // Data refuses a payload past its size cap: photos too long to report go unshown rather than fail the run.
    private fun previewsThatFit(uris: List<String>): Array<String?> {
        val previews = uris.toTypedArray<String?>()
        return if (runCatching { progressOf(done = 0, total = 0, previews) }.isSuccess) previews else emptyArray()
    }

    private fun progressOf(done: Int, total: Int, previews: Array<String?>): Data = Data.Builder()
        .putInt(KEY_DONE, done)
        .putInt(KEY_TOTAL, total)
        .putStringArray(KEY_PREVIEWS, previews)
        .build()

    private fun summaryOf(addedIds: List<String>, skipped: Int, failed: Int): Data = Data.Builder()
        .putStringArray(KEY_ADDED_IDS, addedIds.toTypedArray())
        .putInt(KEY_SKIPPED, skipped)
        .putInt(KEY_FAILED, failed)
        .build()

    companion object {
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_PREVIEWS = "previews"
        const val KEY_ADDED_IDS = "addedIds"
        const val KEY_SKIPPED = "skipped"
        const val KEY_FAILED = "failed"
    }
}
