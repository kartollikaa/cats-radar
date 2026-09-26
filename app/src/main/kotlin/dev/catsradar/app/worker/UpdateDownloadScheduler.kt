package dev.catsradar.app.worker

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.catsradar.domain.update.ReleasePackage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UpdateDownloadScheduler {
    fun download(version: String, apk: ReleasePackage)

    /** The one update download, if any; emits while it works and once more when it ends. */
    fun observe(): Flow<WorkInfo?>
}

class WorkManagerUpdateDownloadScheduler(private val workManager: WorkManager) : UpdateDownloadScheduler {

    override fun download(version: String, apk: ReleasePackage) {
        val request = OneTimeWorkRequestBuilder<DownloadUpdateWorker>()
            .setInputData(UpdateWork.input(version, apk))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(UpdateWork.versionTag(version))
            .build()
        workManager.enqueueUniqueWork(UpdateWork.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun observe(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(UpdateWork.UNIQUE_NAME).map { it.lastOrNull() }
}
