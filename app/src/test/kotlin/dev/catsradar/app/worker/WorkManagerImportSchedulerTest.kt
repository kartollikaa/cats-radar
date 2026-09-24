package dev.catsradar.app.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import dev.catsradar.app.notification.ImportNotifier
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.usecase.ImportSummary
import dev.catsradar.domain.usecase.ImportedPhoto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class WorkManagerImportSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val imported = CopyOnWriteArrayList<List<String>>()

    private var importPhotos: PhotoImport = { uris, _ ->
        imported += uris
        ImportSummary()
    }

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker =
                ImportPhotosWorker(appContext, workerParameters, importPhotos, ImportNotifier(appContext))
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setWorkerFactory(factory).build(),
        )
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun aFullBatchOfGalleryPhotosReachesTheWorkerWhole() {
        val uris = List(Tuning.IMPORT_BATCH_MAX) { index ->
            "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/${TEN_DIGIT_ID + index}"
        }

        WorkManagerImportScheduler(context).startAndAwaitTheRun(uris)

        assertEquals(listOf(uris), imported)
    }

    @Test
    fun aFullBatchOfLongProviderUrisReachesTheWorkerWhole() {
        val uris = List(Tuning.IMPORT_BATCH_MAX) { index -> longProviderUri(index) }
        assertTrue(uris.sumOf { it.length } > Data.MAX_DATA_BYTES)

        WorkManagerImportScheduler(context).startAndAwaitTheRun(uris)

        assertEquals(listOf(uris), imported)
    }

    @Test
    fun aNewPickReplacesTheRunningImportAndLeavesNoBatchBehind() {
        val first = List(2) { index -> longProviderUri(index) }
        val second = List(2) { index -> longProviderUri(index + first.size) }
        val firstRunImporting = CompletableDeferred<Unit>()
        importPhotos = { uris, _ ->
            imported += uris
            if (uris == first) {
                firstRunImporting.complete(Unit)
                awaitCancellation()
            }
            ImportSummary()
        }
        val scheduler = WorkManagerImportScheduler(context)
        scheduler.start(first)
        runBlocking { withTimeout(RUN_TIMEOUT) { firstRunImporting.await() } }

        scheduler.startAndAwaitTheRun(second)

        assertEquals(listOf(first, second), imported)
        assertEquals(emptyList(), context.noBackupFilesDir.walk().filter(File::isFile).toList())
    }

    @Test
    fun aFullBatchReportsEveryPhotoItAdded() {
        val addedIds = List(Tuning.IMPORT_BATCH_MAX) { UUID.randomUUID().toString() }
        importPhotos = { _, _ -> ImportSummary(added = addedIds.map { ImportedPhoto(it, needsLocation = false) }) }

        val run = WorkManagerImportScheduler(context).startAndAwaitTheRun(List(Tuning.IMPORT_BATCH_MAX, ::longProviderUri))

        assertEquals(WorkInfo.State.SUCCEEDED, run.state)
        assertEquals(addedIds, run.outputData.getStringArray(ImportPhotosWorker.KEY_ADDED_IDS)?.toList())
    }

    @Test
    fun aPickWhoseBatchCannotBeStoredEndsItsRunInsteadOfCrashing() {
        val storage = context.noBackupFilesDir
        storage.setWritable(false)
        try {
            val run = WorkManagerImportScheduler(context).startAndAwaitTheRun(List(2, ::longProviderUri))

            assertEquals(WorkInfo.State.SUCCEEDED, run.state)
            assertEquals(emptyList(), imported)
        } finally {
            storage.setWritable(true)
        }
    }

    private fun WorkManagerImportScheduler.startAndAwaitTheRun(uris: List<String>): WorkInfo = runBlocking {
        start(uris)
        withTimeout(RUN_TIMEOUT) { observe().filterNotNull().first { it.state.isFinished } }
    }

    private fun longProviderUri(index: Int): String =
        "content://com.google.android.apps.photos.contentprovider/-1/1/mediakey%3A%2FAF1Qip" +
            "$index".padStart(MEDIA_KEY_LENGTH, 'x') +
            "/ORIGINAL/NONE/image%2Fjpeg/${TEN_DIGIT_ID + index}"

    private companion object {
        const val TEN_DIGIT_ID = 1_000_000_000L
        const val MEDIA_KEY_LENGTH = 80
        val RUN_TIMEOUT = 10.seconds
    }
}
