package dev.catsradar.app.di

import dev.catsradar.presentation.AndroidDateTimeFormatter
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.counter.CounterStateMapper
import dev.catsradar.presentation.counter.CounterStore
import dev.catsradar.presentation.encounters.EncountersStateMapper
import dev.catsradar.presentation.encounters.EncountersStore
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    single<DateTimeFormatter> { AndroidDateTimeFormatter() }
    factoryOf(::CounterStateMapper)
    viewModelOf(::CounterStore)
    factoryOf(::EncountersStateMapper)
    viewModelOf(::EncountersStore)
}
