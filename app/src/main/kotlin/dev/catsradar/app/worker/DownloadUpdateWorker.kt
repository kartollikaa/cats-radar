package dev.catsradar.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dev.catsradar.app.reporting.NonFatalReporter
import dev.catsradar.domain.update.DownloadResult
import dev.catsradar.domain.update.ReleasePackage
import dev.catsradar.domain.usecase.DownloadUpdate
import kotlinx.coroutines.CancellationException

private const val PERCENT = 100

internal object UpdateWork {
    const val UNIQUE_NAME = "update-download"
    const val KEY_VERSION = "version"
    const val KEY_URL = "url"
    const val KEY_SIZE = "size"
    const val KEY_SHA256 = "sha256"
    const val KEY_RECEIVED = "received"
    const val KEY_PATH = "path"
    private const val VERSION_TAG = "update-version:"

    // WorkInfo carries no input data, so the version travels as a tag for every state of the run.
    fun versionTag(version: String) = VERSION_TAG + version

    fun versionOf(tags: Set<String>): String? =
        tags.firstNotNullOfOrNull { tag -> tag.takeIf { it.startsWith(VERSION_TAG) }?.removePrefix(VERSION_TAG) }

    fun input(version: String, apk: ReleasePackage): Data = Data.Builder()
        .putString(KEY_VERSION, version)
        .putString(KEY_URL, apk.url)
        .putLong(KEY_SIZE, apk.sizeBytes)
        .putString(KEY_SHA256, apk.sha256)
        .build()

    fun output(path: String): Data = Data.Builder().putString(KEY_PATH, path).build()
}

class DownloadUpdateWorker(
    context: Context,
    params: WorkerParameters,
    private val downloadUpdate: DownloadUpdate,
    private val reporter: NonFatalReporter,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught") // an unexpected failure must not crash
    override suspend fun doWork(): Result {
        val version = inputData.getString(UpdateWork.KEY_VERSION)
        val url = inputData.getString(UpdateWork.KEY_URL)
        val size = inputData.getLong(UpdateWork.KEY_SIZE, -1)
        if (version == null || url == null || size < 0) return Result.failure()
        return try {
            val apk = ReleasePackage(url, size, inputData.getString(UpdateWork.KEY_SHA256))
            when (val result = downloadUpdate(version, apk, progressReporter(size))) {
                is DownloadResult.Downloaded -> Result.success(UpdateWork.output(result.path))
                is DownloadResult.Failed -> Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            reporter.record(e)
            Result.failure()
        }
    }

    // A progress update is a database write, so only a new whole percent is written.
    private fun progressReporter(size: Long): suspend (Long) -> Unit {
        var reportedPercent = -1L
        return { received ->
            val percent = if (size > 0) received * PERCENT / size else 0
            if (percent != reportedPercent) {
                reportedPercent = percent
                setProgress(
                    Data.Builder().putLong(
                        UpdateWork.KEY_RECEIVED,
                        received
                    ).putLong(UpdateWork.KEY_SIZE, size).build(),
                )
            }
        }
    }
}
