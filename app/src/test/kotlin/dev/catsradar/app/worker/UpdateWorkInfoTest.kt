package dev.catsradar.app.worker

import androidx.work.Data
import androidx.work.WorkInfo
import dev.catsradar.presentation.settings.SettingsIntent
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val RunId = UUID.fromString("0b7a3c52-91d4-4f0e-8a6c-3d2e5f1b9a70")

private fun downloadRun(state: WorkInfo.State, output: Data = Data.EMPTY, progress: Data = Data.EMPTY) = WorkInfo(
    id = RunId,
    state = state,
    tags = setOf(DownloadUpdateWorker::class.java.name, UpdateWork.versionTag("1.5.0-beta")),
    outputData = output,
    progress = progress,
)

class UpdateWorkInfoTest {

    @Test
    fun `a download waiting to start shows as downloading, its size not known yet`() {
        assertEquals(
            SettingsIntent.Update.DownloadProgressed("1.5.0-beta", fraction = null),
            downloadRun(WorkInfo.State.ENQUEUED).toUpdateIntent(),
        )
    }

    @Test
    fun `a running download reports the share of bytes received`() {
        val progress = Data.Builder().putLong(UpdateWork.KEY_RECEIVED, 250).putLong(UpdateWork.KEY_SIZE, 1_000).build()

        assertEquals(
            SettingsIntent.Update.DownloadProgressed("1.5.0-beta", fraction = 0.25f),
            downloadRun(WorkInfo.State.RUNNING, progress = progress).toUpdateIntent(),
        )
    }

    @Test
    fun `a finished download names its run, version and file`() {
        val output = Data.Builder().putString(UpdateWork.KEY_PATH, "/cache/updates/1.5.0-beta.apk").build()

        assertEquals(
            SettingsIntent.Update.DownloadFinished(RunId.toString(), "1.5.0-beta", "/cache/updates/1.5.0-beta.apk"),
            downloadRun(WorkInfo.State.SUCCEEDED, output = output).toUpdateIntent(),
        )
    }

    @Test
    fun `a failed or stopped download reports its run`() {
        assertEquals(
            listOf(
                SettingsIntent.Update.DownloadFailed(RunId.toString()),
                SettingsIntent.Update.DownloadFailed(RunId.toString()),
            ),
            listOf(WorkInfo.State.FAILED, WorkInfo.State.CANCELLED).map { downloadRun(it).toUpdateIntent() },
        )
    }

    @Test
    fun `a run that names no version is not an update download`() {
        assertNull(WorkInfo(id = RunId, state = WorkInfo.State.RUNNING, tags = emptySet()).toUpdateIntent())
    }
}
