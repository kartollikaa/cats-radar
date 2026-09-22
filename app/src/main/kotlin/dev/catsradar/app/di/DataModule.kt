package dev.catsradar.app.di

import dev.catsradar.data.db.CatsDatabase
import dev.catsradar.data.db.EncounterDao
import dev.catsradar.data.db.createCatsDatabase
import dev.catsradar.data.platform.FusedLocationProvider
import dev.catsradar.data.platform.RandomIdGenerator
import dev.catsradar.data.platform.SharedPreferencesDeviceIdProvider
import dev.catsradar.data.platform.VibratorHaptics
import dev.catsradar.data.repository.EncounterRepositoryImpl
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.Haptics
import dev.catsradar.domain.platform.IdGenerator
import dev.catsradar.domain.platform.LocationProvider
import dev.catsradar.domain.repository.EncounterRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single { createCatsDatabase(androidContext()) }
    single<EncounterDao> { get<CatsDatabase>().encounterDao() }
    single<EncounterRepository> { EncounterRepositoryImpl(get()) }
    factory<IdGenerator> { RandomIdGenerator() }
    // createdAtStart: the one-time SharedPreferences read must land at app start, not on the
    // first tap that resolves LogTally.
    single<DeviceIdProvider>(createdAtStart = true) { SharedPreferencesDeviceIdProvider(androidContext(), get()) }
    single<Haptics> { VibratorHaptics(androidContext()) }
    single<LocationProvider> { FusedLocationProvider(androidContext()) }
}
