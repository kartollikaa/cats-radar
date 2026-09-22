package dev.catsradar.app.di

import dev.catsradar.domain.usecase.AttachLocation
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.ImportPhotos
import dev.catsradar.domain.usecase.LogPhoto
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.ObserveEncounterCount
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.ObserveRegion
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.PurgeDeleted
import dev.catsradar.domain.usecase.ResolvePendingPlaces
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.domain.usecase.UndoImport
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
    // Constructed by hand: timeZone has a default, which factoryOf would try to inject.
    factory {
        ImportPhotos(
            encounterRepository = get(),
            placeCellRepository = get(),
            exifReader = get(),
            imageResizer = get(),
            digest = get(),
            sourceFileTime = get(),
            idGenerator = get(),
            deviceIdProvider = get(),
            clock = get(),
            timeZone = get(),
        )
    }
    factoryOf(::UndoLastTally)
    factoryOf(::ObserveEncounterCount)
    factoryOf(::ObserveEncounters)
    factoryOf(::ObserveRegion)
    // Constructed by hand, not factoryOf: reflection injects every constructor parameter
    // including ones with defaults, and the ticker default has no binding to resolve.
    factory { ObserveStats(encounterRepository = get(), clock = get(), timeZone = get()) }
    factoryOf(::AttachLocation)
    factoryOf(::ResolvePendingPlaces)
    // Constructed by hand: purgeAfter has a default, which factoryOf would try to inject.
    factory { PurgeDeleted(encounterRepository = get(), photoStorage = get(), clock = get()) }
    factoryOf(::SetCoat)
    factoryOf(::ObserveEncounter)
    factoryOf(::DeleteEncounter)
    factoryOf(::UndoDelete)
    factoryOf(::UndoImport)
}
