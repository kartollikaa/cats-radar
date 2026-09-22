package dev.catsradar.app

import android.app.Application
import dev.catsradar.app.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class CatsRadarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@CatsRadarApplication)
            modules(presentationModule)
        }
    }
}
