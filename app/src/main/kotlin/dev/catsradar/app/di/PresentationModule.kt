package dev.catsradar.app.di

import dev.catsradar.presentation.AndroidDateTimeFormatter
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CounterStateMapper
import dev.catsradar.presentation.counter.CounterStore
import dev.catsradar.presentation.detail.EncounterDetailStateMapper
import dev.catsradar.presentation.detail.EncounterDetailStore
import dev.catsradar.presentation.encounters.EncountersStateMapper
import dev.catsradar.presentation.encounters.EncountersStore
import dev.catsradar.presentation.map.MapSpotStateMapper
import dev.catsradar.presentation.map.MapSpotStore
import dev.catsradar.presentation.map.MapStateMapper
import dev.catsradar.presentation.map.MapStore
import dev.catsradar.presentation.regions.RegionsStateMapper
import dev.catsradar.presentation.regions.RegionsStore
import dev.catsradar.presentation.settings.SettingsStore
import dev.catsradar.presentation.statistics.StatisticsStateMapper
import dev.catsradar.presentation.statistics.StatisticsStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    single<DateTimeFormatter> { AndroidDateTimeFormatter(androidContext()) }
    factoryOf(::CounterStateMapper)
    viewModelOf(::CounterStore)
    factoryOf(::EncountersStateMapper)
    viewModelOf(::EncountersStore)
    factoryOf(::MapStateMapper)
    viewModelOf(::MapStore)
    factoryOf(::MapSpotStateMapper)
    viewModel { (catIds: Set<String>, coats: Set<CoatOption?>) ->
        MapSpotStore(
            catIds = catIds,
            coats = coats,
            observeEncounters = get(),
            stateMapper = get(),
            clock = get(),
            timeZone = get(),
        )
    }
    factoryOf(::EncounterDetailStateMapper)
    factoryOf(::StatisticsStateMapper)
    viewModelOf(::StatisticsStore)
    factoryOf(::RegionsStateMapper)
    viewModelOf(::SettingsStore)
    viewModel { (parent: dev.catsradar.domain.region.RegionKey?) ->
        RegionsStore(parent = parent, observeRegion = get(), stateMapper = get(), clock = get(), timeZone = get())
    }
    viewModel { (encounterId: String) ->
        EncounterDetailStore(
            encounterId = encounterId,
            observeEncounter = get(),
            deleteEncounter = get(),
            undoDelete = get(),
            setCoat = get(),
            stateMapper = get(),
            clock = get(),
            timeZone = get(),
        )
    }
}
