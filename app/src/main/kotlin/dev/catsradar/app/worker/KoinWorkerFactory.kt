package dev.catsradar.app.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.catsradar.domain.usecase.AttachLocation
import dev.catsradar.domain.usecase.ImportPhotos
import dev.catsradar.domain.usecase.PurgeDeleted
import dev.catsradar.domain.usecase.ResolvePendingPlaces
import org.koin.core.Koin

// WorkManager's own ContentProvider initializer runs before Application.onCreate(), too early for
// Koin to be started; CatsRadarApplication disables it and calls WorkManager.initialize() itself,
// right after startKoin(), with this factory.
class KoinWorkerFactory(private val koin: Koin) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        AttachLocationWorker::class.java.name ->
            AttachLocationWorker(appContext, workerParameters, koin.get<AttachLocation>())
        ImportPhotosWorker::class.java.name ->
            ImportPhotosWorker(appContext, workerParameters, koin.get<ImportPhotos>())
        GeocodePendingCellsWorker::class.java.name ->
            GeocodePendingCellsWorker(appContext, workerParameters, koin.get<ResolvePendingPlaces>())
        PurgeDeletedWorker::class.java.name ->
            PurgeDeletedWorker(appContext, workerParameters, koin.get<PurgeDeleted>())
        else -> null
    }
}
