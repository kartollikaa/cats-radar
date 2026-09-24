package dev.catsradar.app.worker

import androidx.work.Data
import androidx.work.WorkInfo
import dev.catsradar.presentation.counter.CounterIntent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val RunId = UUID.fromString("6f1c2b9e-4d7a-4e5b-9c3d-2a8f0e1b7c64")

private fun workInfo(
    state: WorkInfo.State,
    progress: Data = Data.EMPTY,
    output: Data = Data.EMPTY,
): WorkInfo = WorkInfo(
    id = RunId,
    state = state,
    tags = emptySet(),
    outputData = output,
    progress = progress,
)

private fun progressData(done: Int, total: Int): Data = Data.Builder()
    .putInt(ImportPhotosWorker.KEY_DONE, done)
    .putInt(ImportPhotosWorker.KEY_TOTAL, total)
    .build()

private fun outputData(addedIds: List<String>, skipped: Int, failed: Int): Data = Data.Builder()
    .putStringArray(ImportPhotosWorker.KEY_ADDED_IDS, addedIds.toTypedArray())
    .putInt(ImportPhotosWorker.KEY_SKIPPED, skipped)
    .putInt(ImportPhotosWorker.KEY_FAILED, failed)
    .build()

class ImportWorkInfoTest {

    @Test
    fun `a running worker reports how far it has got`() {
        val intent = workInfo(WorkInfo.State.RUNNING, progress = progressData(done = 3, total = 10)).toCounterIntent()

        assertEquals(CounterIntent.Import.Progressed(done = 3, total = 10), intent)
    }

    @Test
    fun `the first RUNNING emission carries no progress and must not blank the row`() {
        // WorkManager marks the work RUNNING before the worker has called setProgress even once.
        assertNull(workInfo(WorkInfo.State.RUNNING).toCounterIntent())
    }

    @Test
    fun `a finished worker reports what it added, skipped and failed`() {
        val intent = workInfo(
            WorkInfo.State.SUCCEEDED,
            output = outputData(addedIds = listOf("id-1", "id-2"), skipped = 4, failed = 1),
        ).toCounterIntent()

        assertEquals(
            CounterIntent.Import.Finished(
                runId = RunId.toString(),
                addedIds = persistentListOf("id-1", "id-2"),
                skipped = 4,
                failed = 1,
            ),
            intent,
        )
    }

    @Test
    fun `a failed run still ends the progress row rather than leaving it spinning`() {
        val intent = workInfo(WorkInfo.State.FAILED).toCounterIntent()

        assertEquals(
            CounterIntent.Import.Finished(RunId.toString(), persistentListOf(), skipped = 0, failed = 0),
            intent,
        )
    }

    @Test
    fun `a cancelled run ends the same way`() {
        val intent = workInfo(WorkInfo.State.CANCELLED).toCounterIntent()

        assertEquals(
            CounterIntent.Import.Finished(RunId.toString(), persistentListOf(), skipped = 0, failed = 0),
            intent,
        )
    }

    @Test
    fun `work that has not started yet says nothing`() {
        assertNull(workInfo(WorkInfo.State.ENQUEUED).toCounterIntent())
        assertNull(workInfo(WorkInfo.State.BLOCKED).toCounterIntent())
    }
}
