package dev.catsradar.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.catsradar.domain.usecase.AttachLocation

class AttachLocationWorker(
    context: Context,
    params: WorkerParameters,
    private val attachLocation: AttachLocation,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val encounterId = inputData.getString(KEY_ENCOUNTER_ID) ?: return Result.failure()
        attachLocation(encounterId)
        return Result.success()
    }

    companion object {
        const val KEY_ENCOUNTER_ID = "encounter_id"
    }
}
