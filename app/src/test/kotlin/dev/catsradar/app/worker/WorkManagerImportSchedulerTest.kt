package dev.catsradar.app.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import dev.catsradar.domain.Tuning
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

// WorkManager refuses input data past a fixed size, and the picked URIs are all of the import's input.
@RunWith(AndroidJUnit4::class)
class WorkManagerImportSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val handedToWorker = mutableListOf<String>()

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                handedToWorker += workerParameters.inputData.getStringArray(ImportPhotosWorker.KEY_URIS).orEmpty()
                return FinishedWorker(appContext, workerParameters)
            }
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

        WorkManagerImportScheduler(context).start(uris)

        assertEquals(uris, handedToWorker)
    }

    private class FinishedWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }

    private companion object {
        const val TEN_DIGIT_ID = 1_000_000_000L
    }
}
