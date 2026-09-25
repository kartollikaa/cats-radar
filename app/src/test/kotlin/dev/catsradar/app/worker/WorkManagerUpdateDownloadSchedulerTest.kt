package dev.catsradar.app.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dev.catsradar.domain.update.ReleasePackage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class WorkManagerUpdateDownloadSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val apk = ReleasePackage("https://x/cats-radar-1.5.0-beta.apk", sizeBytes = 1_000, sha256 = null)
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun aDownloadWaitsForANetwork() {
        WorkManagerUpdateDownloadScheduler(workManager).download("1.5.0-beta", apk)

        val run = workManager.getWorkInfosForUniqueWork(UpdateWork.UNIQUE_NAME).get().single()
        assertEquals(NetworkType.CONNECTED, run.constraints.requiredNetworkType)
        assertEquals(true, UpdateWork.versionTag("1.5.0-beta") in run.tags)
    }

    @Test
    fun aSecondRequestWhileOneIsPendingStartsNoOtherDownload() {
        val scheduler = WorkManagerUpdateDownloadScheduler(workManager)

        scheduler.download("1.5.0-beta", apk)
        val first = workManager.getWorkInfosForUniqueWork(UpdateWork.UNIQUE_NAME).get().single()
        scheduler.download("1.5.0-beta", apk)

        val runs = workManager.getWorkInfosForUniqueWork(UpdateWork.UNIQUE_NAME).get()
        assertEquals(listOf(first.id), runs.map { it.id })
        assertEquals(WorkInfo.State.ENQUEUED, runs.single().state)
    }
}
