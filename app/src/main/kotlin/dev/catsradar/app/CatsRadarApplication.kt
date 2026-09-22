package dev.catsradar.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import dev.catsradar.app.di.dataModule
import dev.catsradar.app.di.domainModule
import dev.catsradar.app.di.presentationModule
import dev.catsradar.app.di.workerModule
import dev.catsradar.app.notification.ImportNotifier
import dev.catsradar.app.notification.WalkingNotificationSync
import dev.catsradar.app.notification.WalkingNotifier
import dev.catsradar.app.worker.GeocodeWorkScheduler
import dev.catsradar.app.worker.KoinWorkerFactory
import dev.catsradar.app.worker.PurgeWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class CatsRadarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidLogger()
            androidContext(this@CatsRadarApplication)
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin
        // The manifest disables WorkManager's own startup-provider init (it runs before this
        // onCreate(), too early for Koin), so it's initialized by hand right here instead.
        WorkManager.initialize(
            this,
            Configuration.Builder().setWorkerFactory(KoinWorkerFactory(koin)).build(),
        )
        koin.get<ImportNotifier>().ensureChannel()
        koin.get<WalkingNotifier>().ensureChannel()
        // Every process start re-asserts it, including one a lock-screen tap woke up.
        koin.get<WalkingNotificationSync>().start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        GeocodeWorkScheduler.schedule(this)
        PurgeWorkScheduler.schedule(this)
    }
}
