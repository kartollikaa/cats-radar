package dev.catsradar.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dev.catsradar.domain.usecase.ImportPhotos
import kotlinx.coroutines.CancellationException

class ImportPhotosWorker(
    context: Context,
    params: WorkerParameters,
    private val importPhotos: ImportPhotos,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // an unexpected failure must not crash the process
    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(KEY_URIS)?.toList().orEmpty()
        if (uris.isEmpty()) return Result.success(summaryOf(emptyList(), skipped = 0, failed = 0))

        return try {
            val summary = importPhotos(uris) { done, total ->
                setProgressAsync(
                    Data.Builder().putInt(KEY_DONE, done).putInt(KEY_TOTAL, total).build(),
                )
            }
            Result.success(summaryOf(summary.added.map { it.id }, summary.skipped, summary.failed))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never Result.retry(): the picker's read grants die with the process, so a retry
            // after one would import nothing and report every photo as failed.
            Result.failure()
        }
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
