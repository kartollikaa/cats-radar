package dev.catsradar.app.di

import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.os.Vibrator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.analytics.FirebaseAnalytics
import dev.catsradar.app.BuildConfig
import dev.catsradar.data.analytics.FirebaseAnalyticsReporter
import dev.catsradar.data.backup.ZipBackupReader
import dev.catsradar.data.backup.ZipBackupWriter
import dev.catsradar.data.db.CATS_DATABASE_VERSION
import dev.catsradar.data.db.CatsDatabase
import dev.catsradar.data.db.EncounterDao
import dev.catsradar.data.db.PlaceCellDao
import dev.catsradar.data.db.RoomTransactionRunner
import dev.catsradar.data.db.TrackPointDao
import dev.catsradar.data.db.WalkDao
import dev.catsradar.data.db.createCatsDatabase
import dev.catsradar.data.platform.AndroidBuildInfoReader
import dev.catsradar.data.platform.AndroidExifReader
import dev.catsradar.data.platform.AndroidImageResizer
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.data.platform.AndroidReverseGeocoder
import dev.catsradar.data.platform.FusedLocationProvider
import dev.catsradar.data.platform.MediaStoreGalleryItems
import dev.catsradar.data.platform.MediaStoreGallerySaver
import dev.catsradar.data.platform.MediaStoreItemLocator
import dev.catsradar.data.platform.MediaStoreSourceFileTime
import dev.catsradar.data.platform.RandomIdGenerator
import dev.catsradar.data.platform.Sha256Digest
import dev.catsradar.data.platform.SharedPreferencesDeviceIdProvider
import dev.catsradar.data.platform.SharedPreferencesLocationPermissionRequestState
import dev.catsradar.data.platform.SharedPreferencesWalkRecordingState
import dev.catsradar.data.platform.VibratorHaptics
import dev.catsradar.data.repository.EncounterRepositoryImpl
import dev.catsradar.data.repository.PlaceCellRepositoryImpl
import dev.catsradar.data.repository.WalkRepositoryImpl
import dev.catsradar.data.settings.createSettingsRepository
import dev.catsradar.data.update.AndroidInstallPermission
import dev.catsradar.data.update.GitHubReleaseFeed
import dev.catsradar.data.update.HttpPackageDownloader
import dev.catsradar.domain.about.InstalledApp
import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.platform.BackupReader
import dev.catsradar.domain.platform.BackupWriter
import dev.catsradar.domain.platform.BuildInfoReader
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.ExifReader
import dev.catsradar.domain.platform.GalleryItemLocator
import dev.catsradar.domain.platform.GalleryItems
import dev.catsradar.domain.platform.GallerySaver
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.InstallPermission
import dev.catsradar.domain.platform.LocationPermissionRequestState
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.platform.PackageDownloader
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.platform.ReverseGeocoder
import dev.catsradar.domain.platform.SourceFileTime
import dev.catsradar.domain.platform.UpdateSource
import dev.catsradar.domain.platform.WalkRecordingState
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.repository.TransactionRunner
import dev.catsradar.domain.repository.WalkRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

val dataModule = module {
    single { createCatsDatabase(androidContext()) }
    single<EncounterDao> { get<CatsDatabase>().encounterDao() }
    single<EncounterRepository> { EncounterRepositoryImpl(get()) }
    single<Analytics> { FirebaseAnalyticsReporter(FirebaseAnalytics.getInstance(androidContext())) }
    single<PlaceCellDao> { get<CatsDatabase>().placeCellDao() }
    single<PlaceCellRepository> { PlaceCellRepositoryImpl(get()) }
    single<WalkDao> { get<CatsDatabase>().walkDao() }
    single<TrackPointDao> { get<CatsDatabase>().trackPointDao() }
    single<WalkRepository> { WalkRepositoryImpl(get(), get()) }
    single<TransactionRunner> { RoomTransactionRunner(get<CatsDatabase>()) }
    factory<IdGenerator> { RandomIdGenerator() }
    // createdAtStart: the one-time SharedPreferences read must land at app start, not on the
    // first tap that resolves LogTally.
    single<DeviceIdProvider>(createdAtStart = true) {
        SharedPreferencesDeviceIdProvider(preferences("device"), get())
    }
    single<Haptics> { VibratorHaptics(androidContext().getSystemService(Vibrator::class.java)) }
    single<FusedLocationProviderClient> { LocationServices.getFusedLocationProviderClient(androidContext()) }
    // Lazy: building the client reaches Play Services, which only a real location call should do.
    single { FusedLocationProvider(androidContext(), inject(), get()) } bind LocationProvider::class
    single<LocationPermissionRequestState> {
        SharedPreferencesLocationPermissionRequestState(preferences("location_permission"))
    }
    single<WalkRecordingState> { SharedPreferencesWalkRecordingState(preferences("walk_recording")) }
    single { AndroidPhotoStorage(androidContext()) }
    // The resizer needs the concrete store: it writes through it, which the interface does not expose.
    single<PhotoStorage> { get<AndroidPhotoStorage>() }
    single<ExifReader> { AndroidExifReader(androidContext()) }
    single<ImageResizer> { AndroidImageResizer(androidContext(), get()) }
    single<Digest> { Sha256Digest(androidContext()) }
    single<GallerySaver> { MediaStoreGallerySaver(androidContext()) }
    single<GalleryItems> { MediaStoreGalleryItems(androidContext()) }
    single<GalleryItemLocator> { MediaStoreItemLocator(androidContext()) }
    single<SourceFileTime> { MediaStoreSourceFileTime(androidContext()) }
    single<BackupWriter> {
        ZipBackupWriter(
            context = androidContext(),
            photoStorage = get<AndroidPhotoStorage>(),
            deviceIdProvider = get(),
            clock = get(),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }
    single<BackupReader> { ZipBackupReader(androidContext(), get<AndroidPhotoStorage>()) }
    single<ReverseGeocoder> {
        val context = androidContext()
        AndroidReverseGeocoder(newGeocoder = { Geocoder(context) })
    }
    single<SettingsRepository> { createSettingsRepository(androidContext()) }
    single {
        InstalledApp(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            buildType = BuildConfig.BUILD_TYPE,
            applicationId = BuildConfig.APPLICATION_ID,
            commit = BuildConfig.GIT_COMMIT,
            databaseVersion = CATS_DATABASE_VERSION,
        )
    }
    single<BuildInfoReader> { AndroidBuildInfoReader(androidContext(), get()) }
    single<UpdateSource> {
        GitHubReleaseFeed(
            repository = BuildConfig.UPDATE_REPOSITORY,
            userAgent = "CatsRadar/${BuildConfig.VERSION_NAME}",
            apiBase = BuildConfig.UPDATE_API,
        )
    }
    // A cache folder: Android may clear it, which costs only a download.
    single<PackageDownloader> { HttpPackageDownloader(File(androidContext().cacheDir, "updates")) }
    single<InstallPermission> { AndroidInstallPermission(androidContext().packageManager) }
}

// A preferences file's name is where its data lives: renaming one loses everything stored in it.
private fun Scope.preferences(name: String): SharedPreferences =
    androidContext().getSharedPreferences(name, Context.MODE_PRIVATE)
