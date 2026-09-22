package dev.catsradar.app.di

import dev.catsradar.presentation.counter.CounterStateMapper
import dev.catsradar.presentation.counter.CounterStore
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    factoryOf(::CounterStateMapper)
    viewModelOf(::CounterStore)
}
