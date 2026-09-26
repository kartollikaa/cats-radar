package dev.catsradar.app.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.catsradar.app.reporting.RecordingNonFatalReporter
import dev.catsradar.domain.platform.DownloadedPackage
import dev.catsradar.domain.platform.PackageDownloader
import dev.catsradar.domain.update.ReleasePackage
import dev.catsradar.domain.usecase.DownloadUpdate
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DownloadUpdateWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val apk = ReleasePackage("https://x/cats-radar-1.5.0-beta.apk", sizeBytes = 1_000, sha256 = null)
    private val reporter = RecordingNonFatalReporter()

    private val progress = mutableListOf<Long>()

    private fun worker(downloader: PackageDownloader) = TestListenableWorkerBuilder<DownloadUpdateWorker>(context)
        .setInputData(UpdateWork.input("1.5.0-beta", apk))
        .setProgressUpdater { _, _, data ->
            progress += data.getLong(UpdateWork.KEY_RECEIVED, -1)
            androidx.concurrent.futures.ResolvableFuture.create<Void>().apply { set(null) }
        }
        .setWorkerFactory(
            object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters,
                ) = DownloadUpdateWorker(appContext, workerParameters, DownloadUpdate(downloader), reporter)
            },
        )
        .build()

    private class Serving(private val written: DownloadedPackage?) : PackageDownloader {
        var asked: Pair<String, String>? = null
        override suspend fun download(url: String, fileName: String, onProgress: suspend (Long) -> Unit) =
            written.also { asked = url to fileName }

        override suspend fun kept(): List<String> = emptyList()

        override suspend fun discard(path: String) = Unit
    }

    @Test
    fun anIntactPackageEndsTheRunWithItsPath() = runTest {
        val downloader = Serving(DownloadedPackage("/cache/updates/1.5.0-beta.apk", 1_000, "ab"))

        val result = worker(downloader).doWork()

        assertEquals(ListenableWorker.Result.success(UpdateWork.output("/cache/updates/1.5.0-beta.apk")), result)
        assertEquals(apk.url to "1.5.0-beta.apk", downloader.asked)
    }

    @Test
    fun progressIsWrittenOncePerWholePercent() = runTest {
        val stepping = object : PackageDownloader {
            override suspend fun download(url: String, fileName: String, onProgress: suspend (Long) -> Unit) =
                DownloadedPackage("/cache/updates/1.5.0-beta.apk", 1_000, "ab").also {
                    listOf(500L, 505L, 1_000L).forEach { onProgress(it) }
                }

            override suspend fun kept(): List<String> = emptyList()

            override suspend fun discard(path: String) = Unit
        }

        worker(stepping).doWork()

        assertEquals(listOf(500L, 1_000L), progress)
    }

    @Test
    fun aBrokenDownloadFailsTheRun() = runTest {
        assertEquals(ListenableWorker.Result.failure(), worker(Serving(written = null)).doWork())
    }

    @Test
    fun aDamagedPackageFailsTheRun() = runTest {
        val short = Serving(DownloadedPackage("/cache/updates/1.5.0-beta.apk", 999, "ab"))

        assertEquals(ListenableWorker.Result.failure(), worker(short).doWork())
    }

    @Test
    fun anUnexpectedFailureIsReportedAndEndsTheRun() = runTest {
        val throwing = object : PackageDownloader {
            override suspend fun download(url: String, fileName: String, onProgress: suspend (Long) -> Unit) =
                error("disk gone")

            override suspend fun kept(): List<String> = emptyList()

            override suspend fun discard(path: String) = Unit
        }

        assertEquals(ListenableWorker.Result.failure(), worker(throwing).doWork())
        assertEquals(1, reporter.recorded.size)
    }
}
