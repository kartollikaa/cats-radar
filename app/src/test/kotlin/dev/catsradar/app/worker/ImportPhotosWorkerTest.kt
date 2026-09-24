package dev.catsradar.app.worker

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
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
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ImportPhotosWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val photos = listOf(19, 20).map {
        Uri.parse("content://media/picker_get_content/0/com.android.providers.media.photopicker/media/$it")
    }

    private val pickedUris = Array<String?>(photos.size) { photos[it].toString() }

    private fun held(): Set<Uri> = context.contentResolver.persistedUriPermissions.map { it.uri }.toSet()

    private fun worker(importPhotos: PhotoImport): ImportPhotosWorker =
        TestListenableWorkerBuilder<ImportPhotosWorker>(context)
            .setInputData(Data.Builder().putStringArray(ImportPhotosWorker.KEY_URIS, pickedUris).build())
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
    fun holdThePickedPhotos() {
        context.contentResolver.holdReadAccess(photos)
    }

    @Test
    fun aFinishedRunLetsGoOfItsPhotos() = runTest {
        worker { _, _ -> ImportSummary() }.doWork()

        assertEquals(emptySet(), held())
    }

    @Test
    fun aRunThatFailsLetsGoOfItsPhotos() = runTest {
        val result = worker { _, _ -> error("the disk is full") }.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(emptySet(), held())
    }

    @Test
    fun aStoppedRunKeepsItsPhotosForWorkManagersNextAttempt() = runTest {
        val worker = worker { _, _ -> awaitCancellation() }
        val run = launch(start = CoroutineStart.UNDISPATCHED) { worker.doWork() }

        run.cancelAndJoin()

        assertEquals(photos.toSet(), held())
    }
}
