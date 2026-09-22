package dev.catsradar.app.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.data.db.CatsDatabase
import dev.catsradar.data.db.EncounterDao
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.presentation.detail.EncounterDetailStore
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

// @Config forces a plain Application rather than the manifest's CatsRadarApplication, whose own
// onCreate() would start Koin itself and collide with the startKoin() below.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class KoinRuntimeResolutionTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    // verify() (KoinModulesTest) proves the graph is *declarable* by walking constructor
    // parameters; it cannot see a type obtained by hand with get()/koinInject() inside a lambda
    // binding or a composable, because no reflected constructor ever names that type. This starts
    // the real modules and asks for exactly those types, so a deleted binding fails here instead
    // of on the user's first tap.
    @Test
    fun `types resolved outside constructor injection are bound`() {
        val koin = startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Context>())
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin

        assertNotNull(koin.get<Context>())
        assertNotNull(koin.get<CatsDatabase>())
        assertNotNull(koin.get<EncounterDao>())
        assertNotNull(koin.get<Haptics>())
        assertNotNull(koin.get<LocationProvider>())
        assertNotNull(koin.get<LocationAttachScheduler>())
        assertNotNull(koin.get<EncounterDetailStore> { parametersOf("any-id") })
    }
}
