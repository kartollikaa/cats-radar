package dev.catsradar.app.di

import dev.catsradar.app.worker.BackupScheduler
import dev.catsradar.app.worker.ImportScheduler
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.app.worker.WorkManagerBackupScheduler
import dev.catsradar.app.worker.WorkManagerImportScheduler
import dev.catsradar.app.worker.WorkManagerLocationAttachScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val workerModule = module {
    single<LocationAttachScheduler> { WorkManagerLocationAttachScheduler(androidContext()) }
    single<ImportScheduler> { WorkManagerImportScheduler(androidContext()) }
    single<BackupScheduler> { WorkManagerBackupScheduler(androidContext()) }
}
