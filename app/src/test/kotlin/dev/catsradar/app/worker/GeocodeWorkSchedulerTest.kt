package dev.catsradar.app.worker

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// USE_TIME_BASED_SCHEDULING, and no constraint is ever marked met: requests stay queued, which is the
// state these tests read.
@RunWith(AndroidJUnit4::class)
class GeocodeWorkSchedulerTest {
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
    fun `naming untried cells waits for the network`() {
        GeocodeWorkScheduler.nameUntriedCells(context)

        assertEquals(NetworkType.CONNECTED, untriedWork().single().constraints.requiredNetworkType)
    }

    @SuppressLint("RestrictedApi")
    @Test
    fun `naming untried cells runs the untried pass, not the full one`() {
        GeocodeWorkScheduler.nameUntriedCells(context)

        val dao = WorkManagerImpl.getInstance(context).workDatabase.workSpecDao()
        val input = dao.getWorkSpec(untriedWork().single().id.toString())!!.input
        assertTrue(input.getBoolean(GeocodePendingCellsWorker.KEY_UNTRIED_ONLY, false))
    }

    // Kept instead, a pass already past a cell written meanwhile would leave it for the periodic slot.
    @Test
    fun `a new request starts the pass over, so at most one is ever queued`() {
        GeocodeWorkScheduler.nameUntriedCells(context)
        val first = untriedWork().single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)

        GeocodeWorkScheduler.nameUntriedCells(context)

        val waiting = untriedWork().filterNot { it.state.isFinished }
        assertNotEquals(first.id, waiting.single().id)
    }

    private fun untriedWork(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(GeocodeWorkScheduler.UNTRIED_UNIQUE_NAME).get()
}
