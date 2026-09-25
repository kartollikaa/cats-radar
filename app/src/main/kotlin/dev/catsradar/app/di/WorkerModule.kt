package dev.catsradar.app.di

import android.app.ActivityManager
import androidx.core.app.NotificationManagerCompat
import androidx.glance.appwidget.updateAll
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dev.catsradar.app.navigation.ScreenViewTracker
import dev.catsradar.app.notification.ImportNotifier
import dev.catsradar.app.notification.WalkRecordingControl
import dev.catsradar.app.notification.WalkingNotificationSync
import dev.catsradar.app.notification.WalkingNotifications
import dev.catsradar.app.notification.WalkingNotifier
import dev.catsradar.app.reporting.CrashlyticsNonFatalReporter
import dev.catsradar.app.reporting.NonFatalReporter
import dev.catsradar.app.widget.CatsRadarWidget
import dev.catsradar.app.widget.WidgetRedraw
import dev.catsradar.app.widget.WidgetRefresh
import dev.catsradar.app.worker.BackupScheduler
import dev.catsradar.app.worker.GeocodeWorkScheduler
import dev.catsradar.app.worker.ImportBatches
import dev.catsradar.app.worker.ImportScheduler
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.app.worker.PlaceNamingScheduler
import dev.catsradar.app.worker.PlaceNamingTrigger
import dev.catsradar.app.worker.PurgeWorkScheduler
import dev.catsradar.app.worker.WorkManagerBackupScheduler
import dev.catsradar.app.worker.WorkManagerImportScheduler
import dev.catsradar.app.worker.WorkManagerLocationAttachScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val workerModule = module {
    single { NotificationManagerCompat.from(androidContext()) }
    // Schedulers take this Lazy: Koin may build them before WorkManager.initialize() has run.
    single { WorkManager.getInstance(androidContext()) }
    single<ActivityManager> { androidContext().getSystemService(ActivityManager::class.java) }
    single { ImportNotifier(androidContext(), get()) }
    single { WalkRecordingControl(androidContext()) }
    single { WalkingNotifier(androidContext(), get(), get(), get()) }
    single<WalkingNotifications> { get<WalkingNotifier>() }
    single { WalkingNotificationSync(get(), get(), get(), get()) }
    single<WidgetRedraw> { WidgetRedraw { CatsRadarWidget().updateAll(androidContext()) } }
    single { WidgetRefresh(get(), get()) }
    single { GeocodeWorkScheduler(inject()) }
    single<PlaceNamingScheduler> { get<GeocodeWorkScheduler>() }
    single { PlaceNamingTrigger(get(), get()) }
    single { PurgeWorkScheduler(inject()) }
    single { ImportBatches(androidContext()) }
    single<LocationAttachScheduler> { WorkManagerLocationAttachScheduler(inject()) }
    single<ImportScheduler> { WorkManagerImportScheduler(inject(), get()) }
    single<BackupScheduler> { WorkManagerBackupScheduler(inject()) }
    // Lazy: getInstance() throws in a process where FirebaseApp never started, a JVM test among them.
    single<NonFatalReporter> { CrashlyticsNonFatalReporter(lazy { FirebaseCrashlytics.getInstance() }) }
    single { ScreenViewTracker(get()) }
}
