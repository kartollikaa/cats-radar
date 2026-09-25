package dev.catsradar.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dev.catsradar.app.di.dataModule
import dev.catsradar.app.di.domainModule
import dev.catsradar.app.di.presentationModule
import dev.catsradar.app.di.workerModule
import dev.catsradar.app.navigation.ScreenViewTracker
import dev.catsradar.app.notification.ImportNotifier
import dev.catsradar.app.notification.WalkingNotificationSync
import dev.catsradar.app.notification.WalkingNotifier
import dev.catsradar.app.reporting.NonFatalReporter
import dev.catsradar.app.reporting.tagReports
import dev.catsradar.app.widget.WidgetRefresh
import dev.catsradar.app.worker.GeocodeWorkScheduler
import dev.catsradar.app.worker.KoinWorkerFactory
import dev.catsradar.app.worker.PlaceNamingTrigger
import dev.catsradar.app.worker.PurgeWorkScheduler
import dev.catsradar.domain.usecase.EndInterruptedWalk
import dev.catsradar.domain.usecase.FollowWalkingMode
import dev.catsradar.domain.usecase.RegeneratePhotoCopies
import dev.catsradar.domain.usecase.RepairPlaceCells
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class CatsRadarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        tagReports(FirebaseCrashlytics.getInstance(), FirebaseAnalytics.getInstance(this), BuildConfig.BUILD_TYPE)
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
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // A permission dialog over the app pauses it, so an answer to one is seen when it resumes.
        val appOnScreen = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.RESUMED) }
        appScope.launch {
            // Settled first: a recording cut off by process death turns off the mode the two below follow.
            koin.get<EndInterruptedWalk>()()
            // Every process start re-asserts it, including one a lock-screen tap woke up.
            koin.get<WalkingNotificationSync>().start(appScope, appOnScreen)
            koin.get<FollowWalkingMode>()()
        }
        val screenViews = koin.get<ScreenViewTracker>()
        // Process-wide, so a rotation, which restarts the activity but not the process, is not a new visit.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> screenViews.onAppResumed()
                    Lifecycle.Event.ON_PAUSE -> screenViews.onAppPaused()
                    Lifecycle.Event.ON_STOP -> screenViews.onAppStopped()
                    else -> Unit
                }
            },
        )
        koin.get<WidgetRefresh>().start(appScope)
        koin.get<PlaceNamingTrigger>().start(appScope)
        StartupRepairs(
            repairs = listOf({ koin.get<RepairPlaceCells>()() }, { koin.get<RegeneratePhotoCopies>()() }),
            reporter = koin.get<NonFatalReporter>(),
        ).launchIn(appScope)
        koin.get<GeocodeWorkScheduler>().schedule()
        koin.get<PurgeWorkScheduler>().schedule()
    }
}
