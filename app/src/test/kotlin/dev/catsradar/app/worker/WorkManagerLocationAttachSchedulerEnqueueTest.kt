package dev.catsradar.app.worker

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.model.WorkSpec
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// No public WorkInfo field exposes "expedited"; reading it back off the enqueued WorkSpec via
// WorkManagerImpl's own test database is the documented way to assert on it.
@RunWith(AndroidJUnit4::class)
class WorkManagerLocationAttachSchedulerEnqueueTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setWorkerFactory(ParkingWorkerFactory).build(),
            WorkManagerTestInitHelper.ExecutorsMode.USE_TIME_BASED_SCHEDULING,
        )
    }

    private fun scheduler(sdkInt: Int) = WorkManagerLocationAttachScheduler(WorkManager.getInstance(context), sdkInt)

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `enqueues expedited work on API 31 and above`() {
        scheduler(sdkInt = 31).schedule("encounter-1")

        assertTrue(enqueuedWorkSpec("encounter-1").expedited)
    }

    @Test
    fun `enqueues ordinary work below API 31`() {
        scheduler(sdkInt = 30).schedule("encounter-2")

        assertFalse(enqueuedWorkSpec("encounter-2").expedited)
    }

    @Test
    fun `cancel cancels the unique work scheduled for that encounter id`() {
        val scheduler = scheduler(sdkInt = 30)
        scheduler.schedule("encounter-3")
        scheduler.cancel("encounter-3")
        shadowOf(Looper.getMainLooper()).idle()

        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("encounter-3").get()
        assertEquals(listOf(WorkInfo.State.CANCELLED), workInfos.map { it.state })
    }

    // getWorkInfosForUniqueWork(id).get() blocks until the enqueue's async DB write has actually
    // landed - reading the DAO directly right after schedule() can race ahead of it and see
    // nothing yet, under USE_TIME_BASED_SCHEDULING's real background executor.
    @SuppressLint("RestrictedApi")
    private fun enqueuedWorkSpec(encounterId: String): WorkSpec {
        val workInfo = WorkManager.getInstance(context).getWorkInfosForUniqueWork(encounterId).get().single()
        val dao = WorkManagerImpl.getInstance(context).workDatabase.workSpecDao()
        return dao.getWorkSpec(workInfo.id.toString())!!
    }
}

// Time-based scheduling starts unconstrained work on real executors the moment it is enqueued; a run that
// finished first would leave cancel() nothing to cancel, so every worker here waits until it is stopped.
private object ParkingWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker = ParkedWorker(appContext, workerParameters)
}

private class ParkedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = awaitCancellation()
}
