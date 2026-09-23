package dev.catsradar.app.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.worker.LocationAttachScheduler
import dev.catsradar.data.db.CatsDatabase
import dev.catsradar.data.db.EncounterDao
import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.ExifReader
import dev.catsradar.domain.platform.GallerySaver
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.usecase.ExportBackup
import dev.catsradar.domain.usecase.ImportBackup
import dev.catsradar.domain.usecase.ImportPhotos
import dev.catsradar.domain.usecase.LogPhoto
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.RecordTrackPoint
import dev.catsradar.presentation.detail.EncounterDetailStore
import dev.catsradar.presentation.regions.RegionsStore
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
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
        assertNotNull(koin.get<ExifReader>())
        assertNotNull(koin.get<ImportPhotos>())
        assertNotNull(koin.get<ExportBackup>())
        assertNotNull(koin.get<ImportBackup>())
        assertNotNull(koin.get<ImageResizer>())
        assertNotNull(koin.get<Digest>())
        assertNotNull(koin.get<GallerySaver>())
        assertNotNull(koin.get<PhotoStorage>())
        // verify() treats a constructor parameter with a default as satisfied, but factoryOf's
        // reflection still tries to inject it; only actually building the object catches that.
        assertNotNull(koin.get<ObserveStats>())
        assertNotNull(koin.get<LogPhoto>())
        assertNotNull(koin.get<EncounterDetailStore> { parametersOf("any-id") })
        // Both the root (null parent) and a drilled-in level, because they take different paths.
        assertNotNull(koin.get<RegionsStore> { parametersOf(null) })
        assertNotNull(koin.get<RegionsStore> { parametersOf(RegionKey.Country("ES")) })
    }

    @Test
    fun `every caller recording a walk gets the same recorder, so fixes take turns`() {
        val koin = startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Context>())
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin

        assertSame(koin.get<RecordTrackPoint>(), koin.get<RecordTrackPoint>())
    }
}
