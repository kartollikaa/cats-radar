package dev.catsradar.app.di

import dev.catsradar.presentation.AndroidDateTimeFormatter
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.counter.CounterStateMapper
import dev.catsradar.presentation.counter.CounterStore
import dev.catsradar.presentation.detail.EncounterDetailStateMapper
import dev.catsradar.presentation.detail.EncounterDetailStore
import dev.catsradar.presentation.encounters.EncountersStateMapper
import dev.catsradar.presentation.encounters.EncountersStore
import dev.catsradar.presentation.statistics.StatisticsStateMapper
import dev.catsradar.presentation.statistics.StatisticsStore
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    single<DateTimeFormatter> { AndroidDateTimeFormatter() }
    factoryOf(::CounterStateMapper)
    viewModelOf(::CounterStore)
    factoryOf(::EncountersStateMapper)
    viewModelOf(::EncountersStore)
    factoryOf(::EncounterDetailStateMapper)
    factoryOf(::StatisticsStateMapper)
    viewModelOf(::StatisticsStore)
    viewModel { (encounterId: String) ->
        EncounterDetailStore(
            encounterId = encounterId,
            observeEncounter = get(),
            deleteEncounter = get(),
            undoDelete = get(),
            stateMapper = get(),
            clock = get(),
            timeZone = get(),
        )
    }
}
