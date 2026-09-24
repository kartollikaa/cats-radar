package dev.catsradar.app.worker

import androidx.work.Data
import androidx.work.WorkInfo
import dev.catsradar.presentation.counter.CounterIntent
import kotlinx.collections.immutable.toImmutableList
import java.util.UUID

/**
 * What the screen should be told about the one import run, or null when it should be told nothing.
 *
 * A run that ends without output — cancelled, or failed before it could report — produces an empty
 * summary rather than nothing, so the progress row always has something that clears it.
 */
internal fun WorkInfo.toCounterIntent(): CounterIntent? = when (state) {
    WorkInfo.State.RUNNING -> progress.toProgressIntent()
    WorkInfo.State.SUCCEEDED -> outputData.toFinishedIntent(id)
    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> Data.EMPTY.toFinishedIntent(id)
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> null
}

private fun Data.toProgressIntent(): CounterIntent? {
    val total = getInt(ImportPhotosWorker.KEY_TOTAL, 0)
    // WorkManager delivers one RUNNING emission before the worker has posted any progress of its
    // own; its empty Data would otherwise read as "0 of 0" and blank the row that was just shown.
    if (total == 0) return null
    return CounterIntent.Import.Progressed(done = getInt(ImportPhotosWorker.KEY_DONE, 0), total = total)
}

private fun Data.toFinishedIntent(runId: UUID): CounterIntent = CounterIntent.Import.Finished(
    runId = runId.toString(),
    addedIds = getStringArray(ImportPhotosWorker.KEY_ADDED_IDS)?.toList().orEmpty().toImmutableList(),
    skipped = getInt(ImportPhotosWorker.KEY_SKIPPED, 0),
    failed = getInt(ImportPhotosWorker.KEY_FAILED, 0),
)
