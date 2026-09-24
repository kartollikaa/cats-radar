package dev.catsradar.app.worker

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.catsradar.app.notification.ImportNotifier
import dev.catsradar.app.photo.holdReadAccess
import dev.catsradar.domain.usecase.ImportSummary
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ImportPhotosWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val photos = listOf(19, 20).map {
        Uri.parse("content://media/picker_get_content/0/com.android.providers.media.photopicker/media/$it")
    }

    private val runId = UUID.randomUUID()

    private val batches = ImportBatches(context)

    private fun held(): Set<Uri> = context.contentResolver.persistedUriPermissions.map { it.uri }.toSet()

    private fun worker(importPhotos: PhotoImport): ImportPhotosWorker =
        TestListenableWorkerBuilder<ImportPhotosWorker>(context)
            .setId(runId)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        ImportPhotosWorker(appContext, workerParameters, importPhotos, ImportNotifier(appContext))
                },
            )
            .build()

    @Before
    fun pickThePhotos() {
        context.contentResolver.holdReadAccess(photos)
        batches.replaceWith(runId, photos.map(Uri::toString))
    }

    @Test
    fun aRunImportsTheBatchStoredUnderItsId() = runTest {
        val imported = mutableListOf<String>()

        worker { uris, _ -> ImportSummary().also { imported += uris } }.doWork()

        assertEquals(photos.map(Uri::toString), imported)
    }

    @Test
    fun aRunWithNoStoredBatchImportsNothing() = runTest {
        batches.delete(runId)
        val imported = mutableListOf<String>()

        val result = worker { uris, _ -> ImportSummary().also { imported += uris } }.doWork()

        assertEquals(ListenableWorker.Result.Success::class, result::class)
        assertEquals(emptyList(), imported)
    }

    @Test
    fun aRunWhoseBatchCannotBeReadImportsNothingAndLeavesNoBatchBehind() = runTest {
        storedFiles().single().writeText("[\"content://media/picker_get_content/0/com.andr")
        val imported = mutableListOf<String>()

        val result = worker { uris, _ -> ImportSummary().also { imported += uris } }.doWork()

        assertEquals(ListenableWorker.Result.Success::class, result::class)
        assertEquals(emptyList(), imported)
        assertEquals(emptyList(), storedFiles())
    }

    @Test
    fun aFinishedRunLetsGoOfItsPhotos() = runTest {
        worker { _, _ -> ImportSummary() }.doWork()

        assertEquals(emptySet(), held())
    }

    @Test
    fun aFinishedRunLeavesNoBatchBehind() = runTest {
        worker { _, _ -> ImportSummary() }.doWork()

        assertEquals(emptyList(), storedFiles())
    }

    @Test
    fun aRunThatFailsLetsGoOfItsPhotos() = runTest {
        val result = worker { _, _ -> error("the disk is full") }.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(emptySet(), held())
    }

    @Test
    fun aRunThatFailsLeavesNoBatchBehind() = runTest {
        worker { _, _ -> error("the disk is full") }.doWork()

        assertEquals(emptyList(), storedFiles())
    }

    @Test
    fun aStoppedRunKeepsItsPhotosForWorkManagersNextAttempt() = runTest {
        val worker = worker { _, _ -> awaitCancellation() }
        val run = launch(start = CoroutineStart.UNDISPATCHED) { worker.doWork() }

        run.cancelAndJoin()

        assertEquals(photos.toSet(), held())
    }

    @Test
    fun aStoppedRunKeepsItsBatchForWorkManagersNextAttempt() = runTest {
        val worker = worker { _, _ -> awaitCancellation() }
        val run = launch(start = CoroutineStart.UNDISPATCHED) { worker.doWork() }

        run.cancelAndJoin()

        assertEquals(photos.map(Uri::toString), batches.read(runId))
        assertEquals(1, storedFiles().size)
    }

    private fun storedFiles(): List<File> = context.noBackupFilesDir.walk().filter(File::isFile).toList()
}
