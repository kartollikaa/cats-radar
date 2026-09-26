package dev.catsradar.app.worker

import androidx.work.WorkInfo
import dev.catsradar.presentation.settings.SettingsIntent

/** What the Settings screen should be told about the one update download, or null when it is not one. */
internal fun WorkInfo.toUpdateIntent(): SettingsIntent.Update? {
    val version = UpdateWork.versionOf(tags) ?: return null
    return when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> SettingsIntent.Update.DownloadProgressed(version, null)
        WorkInfo.State.RUNNING -> SettingsIntent.Update.DownloadProgressed(version, receivedShare())
        WorkInfo.State.SUCCEEDED -> outputData.getString(UpdateWork.KEY_PATH)
            ?.let { SettingsIntent.Update.DownloadFinished(id.toString(), version, it) }
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> SettingsIntent.Update.DownloadFailed(id.toString())
    }
}

private fun WorkInfo.receivedShare(): Float? {
    val size = progress.getLong(UpdateWork.KEY_SIZE, 0)
    return if (size > 0) progress.getLong(UpdateWork.KEY_RECEIVED, 0).toFloat() / size else null
}
