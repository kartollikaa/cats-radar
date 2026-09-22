package dev.catsradar.app.di

import dev.catsradar.domain.usecase.AttachLocation
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.LogPhoto
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.ObserveEncounterCount
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.domain.usecase.UndoLastTally
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import kotlin.time.Clock

val domainModule = module {
    single<Clock> { Clock.System }
    single { TimeZone.currentSystemDefault() }
    factoryOf(::LogTally)
    factoryOf(::LogPhoto)
    factoryOf(::UndoLastTally)
    factoryOf(::ObserveEncounterCount)
    factoryOf(::ObserveEncounters)
    factoryOf(::AttachLocation)
    factoryOf(::ObserveEncounter)
    factoryOf(::DeleteEncounter)
    factoryOf(::UndoDelete)
}
