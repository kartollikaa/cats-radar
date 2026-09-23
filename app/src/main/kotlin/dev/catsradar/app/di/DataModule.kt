package dev.catsradar.app.di

import dev.catsradar.app.BuildConfig
import dev.catsradar.data.backup.ZipBackupReader
import dev.catsradar.data.backup.ZipBackupWriter
import dev.catsradar.data.db.CatsDatabase
import dev.catsradar.data.db.EncounterDao
import dev.catsradar.data.db.PlaceCellDao
import dev.catsradar.data.db.WalkDao
import dev.catsradar.data.db.createCatsDatabase
import dev.catsradar.data.platform.AndroidExifReader
import dev.catsradar.data.platform.AndroidImageResizer
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.data.platform.AndroidReverseGeocoder
import dev.catsradar.data.platform.FusedLocationProvider
import dev.catsradar.data.platform.MediaStoreGallerySaver
import dev.catsradar.data.platform.MediaStoreSourceFileTime
import dev.catsradar.data.platform.RandomIdGenerator
import dev.catsradar.data.platform.Sha256Digest
import dev.catsradar.data.platform.SharedPreferencesDeviceIdProvider
import dev.catsradar.data.platform.SharedPreferencesLocationPermissionRequestState
import dev.catsradar.data.platform.VibratorHaptics
import dev.catsradar.data.repository.EncounterRepositoryImpl
import dev.catsradar.data.repository.PlaceCellRepositoryImpl
import dev.catsradar.data.repository.WalkRepositoryImpl
import dev.catsradar.data.settings.createSettingsRepository
import dev.catsradar.domain.platform.BackupReader
import dev.catsradar.domain.platform.BackupWriter
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.Digest
import dev.catsradar.domain.platform.ExifReader
import dev.catsradar.domain.platform.GallerySaver
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.LocationPermissionRequestState
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.platform.ReverseGeocoder
import dev.catsradar.domain.platform.SourceFileTime
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.repository.WalkRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single { createCatsDatabase(androidContext()) }
    single<EncounterDao> { get<CatsDatabase>().encounterDao() }
    single<EncounterRepository> { EncounterRepositoryImpl(get()) }
    single<PlaceCellDao> { get<CatsDatabase>().placeCellDao() }
    single<PlaceCellRepository> { PlaceCellRepositoryImpl(get()) }
    single<WalkDao> { get<CatsDatabase>().walkDao() }
    single<WalkRepository> { WalkRepositoryImpl(get()) }
    factory<IdGenerator> { RandomIdGenerator() }
    // createdAtStart: the one-time SharedPreferences read must land at app start, not on the
    // first tap that resolves LogTally.
    single<DeviceIdProvider>(createdAtStart = true) { SharedPreferencesDeviceIdProvider(androidContext(), get()) }
    single<Haptics> { VibratorHaptics(androidContext()) }
    single<LocationProvider> { FusedLocationProvider(androidContext()) }
    single<LocationPermissionRequestState> { SharedPreferencesLocationPermissionRequestState(androidContext()) }
    single { AndroidPhotoStorage(androidContext()) }
    // The resizer needs the concrete store: it writes through it, which the interface does not expose.
    single<PhotoStorage> { get<AndroidPhotoStorage>() }
    single<ExifReader> { AndroidExifReader(androidContext()) }
    single<ImageResizer> { AndroidImageResizer(androidContext(), get()) }
    single<Digest> { Sha256Digest(androidContext()) }
    single<GallerySaver> { MediaStoreGallerySaver(androidContext()) }
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
    single<ReverseGeocoder> { AndroidReverseGeocoder(androidContext()) }
    single<SettingsRepository> { createSettingsRepository(androidContext()) }
}
