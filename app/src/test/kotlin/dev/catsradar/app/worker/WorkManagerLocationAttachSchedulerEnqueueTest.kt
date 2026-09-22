package dev.catsradar.app.worker

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.model.WorkSpec
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// No public WorkInfo field exposes "expedited"; reading it back off the enqueued WorkSpec via
// WorkManagerImpl's own test database is the documented way to assert on it.
//
// USE_TIME_BASED_SCHEDULING, not the legacy SynchronousExecutor mode: under a synchronous
// executor, GreedyScheduler runs (and fails, since no WorkerFactory here can build
// AttachLocationWorker) newly enqueued work inline within enqueue() itself, before this test's
// own cancel() call ever runs - there is no "still pending" state left to cancel.
@RunWith(AndroidJUnit4::class)
class WorkManagerLocationAttachSchedulerEnqueueTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            WorkManagerTestInitHelper.ExecutorsMode.USE_TIME_BASED_SCHEDULING,
        )
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `enqueues expedited work on API 31 and above`() {
        WorkManagerLocationAttachScheduler(context, sdkInt = 31).schedule("encounter-1")

        assertTrue(enqueuedWorkSpec("encounter-1").expedited)
    }

    @Test
    fun `enqueues ordinary work below API 31`() {
        WorkManagerLocationAttachScheduler(context, sdkInt = 30).schedule("encounter-2")

        assertFalse(enqueuedWorkSpec("encounter-2").expedited)
    }

    @Test
    fun `cancel cancels the unique work scheduled for that encounter id`() {
        val scheduler = WorkManagerLocationAttachScheduler(context, sdkInt = 30)
        scheduler.schedule("encounter-3")
        scheduler.cancel("encounter-3")
        shadowOf(Looper.getMainLooper()).idle()

        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("encounter-3").get()
        assertTrue(workInfos.isNotEmpty())
        assertTrue(workInfos.all { it.state == WorkInfo.State.CANCELLED })
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
