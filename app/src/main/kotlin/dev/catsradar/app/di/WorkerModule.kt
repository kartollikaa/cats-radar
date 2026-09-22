package dev.catsradar.app.di

import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.app.worker.WorkManagerLocationAttachScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val workerModule = module {
    single<LocationAttachScheduler> { WorkManagerLocationAttachScheduler(androidContext()) }
}
