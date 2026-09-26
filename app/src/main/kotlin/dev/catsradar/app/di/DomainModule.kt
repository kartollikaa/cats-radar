package dev.catsradar.app.di

import dev.catsradar.domain.usecase.AttachLocation
import dev.catsradar.domain.usecase.AttachPhoto
import dev.catsradar.domain.usecase.CheckForUpdate
import dev.catsradar.domain.usecase.DeleteEncounter
import dev.catsradar.domain.usecase.DeleteEncounters
import dev.catsradar.domain.usecase.EndInterruptedWalk
import dev.catsradar.domain.usecase.EndWalk
import dev.catsradar.domain.usecase.ExportBackup
import dev.catsradar.domain.usecase.FollowWalkingMode
import dev.catsradar.domain.usecase.ImportBackup
import dev.catsradar.domain.usecase.ImportPhotos
import dev.catsradar.domain.usecase.LocatePhone
import dev.catsradar.domain.usecase.LogPhoto
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.domain.usecase.ObserveEncounterPlace
import dev.catsradar.domain.usecase.ObserveEncounters
import dev.catsradar.domain.usecase.ObserveOpenWalk
import dev.catsradar.domain.usecase.ObserveOutingTracks
import dev.catsradar.domain.usecase.ObserveRegion
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.ObserveTodayCount
import dev.catsradar.domain.usecase.ObserveUntriedPlaceCells
import dev.catsradar.domain.usecase.ObserveWalkElapsed
import dev.catsradar.domain.usecase.ObserveWalkStats
import dev.catsradar.domain.usecase.ObserveWalkTracks
import dev.catsradar.domain.usecase.PurgeDeleted
import dev.catsradar.domain.usecase.RecordTrackPoint
import dev.catsradar.domain.usecase.RecordWalk
import dev.catsradar.domain.usecase.RepairPlaceCells
import dev.catsradar.domain.usecase.ResolveGalleryLink
import dev.catsradar.domain.usecase.ResolvePendingPlaces
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.domain.usecase.SetLocationByHand
import dev.catsradar.domain.usecase.StartWalk
import dev.catsradar.domain.usecase.UndoDelete
import dev.catsradar.domain.usecase.UndoDeleteEncounters
import dev.catsradar.domain.usecase.UndoImport
import dev.catsradar.domain.usecase.UndoLastTally
import dev.catsradar.domain.usecase.WhereToLook
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.time.Clock

val domainModule = module {
    single<Clock> { Clock.System }
    single { TimeZone.currentSystemDefault() }
    factoryOf(::LogTally)
    factoryOf(::StartWalk)
    factoryOf(::EndWalk)
    factoryOf(::FollowWalkingMode)
    factoryOf(::EndInterruptedWalk)
    factoryOf(::RecordWalk)
    singleOf(::RecordTrackPoint) // one instance, so every caller waits on the same turn
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
            galleryItemLocator = get(),
            idGenerator = get(),
            deviceIdProvider = get(),
            clock = get(),
            timeZone = get(),
            analytics = get(),
        )
    }
    factoryOf(::UndoLastTally)
    factoryOf(::ObserveEncounters)
    factoryOf(::ObserveEncounterPlace)
    factory {
        ObserveRegion(encounterRepository = get(), placeCellRepository = get(), computeDispatcher = Dispatchers.Default)
    }
    // Constructed by hand, not factoryOf: reflection injects every constructor parameter
    // including ones with defaults, and the ticker default has no binding to resolve.
    factory { ObserveStats(encounterRepository = get(), clock = get(), timeZone = get()) }
    factory { ObserveOpenWalk(walkRepository = get()) }
    factory { ObserveWalkElapsed(observeOpenWalk = get(), clock = get()) }
    factoryOf(::ObserveWalkTracks)
    factory { ObserveWalkStats(observeWalkTracks = get(), computeDispatcher = Dispatchers.Default) }
    factoryOf(::ObserveOutingTracks)
    // No zone passed: this one outlives a trip across time zones, so it reads the zone each time.
    factory { ObserveTodayCount(encounterRepository = get(), clock = get()) }
    factoryOf(::AttachLocation)
    factoryOf(::ResolvePendingPlaces)
    factoryOf(::ObserveUntriedPlaceCells)
    factoryOf(::RepairPlaceCells)
    // Constructed by hand: purgeAfter has a default, which factoryOf would try to inject.
    factory { PurgeDeleted(encounterRepository = get(), photoStorage = get(), clock = get()) }
    factoryOf(::SetCoat)
    factoryOf(::SetLocationByHand)
    factoryOf(::WhereToLook)
    factoryOf(::LocatePhone)
    factoryOf(::AttachPhoto)
    factoryOf(::ObserveEncounter)
    factoryOf(::ResolveGalleryLink)
    factoryOf(::DeleteEncounter)
    factoryOf(::UndoDelete)
    factoryOf(::DeleteEncounters)
    factoryOf(::UndoDeleteEncounters)
    factoryOf(::UndoImport)
    factoryOf(::ExportBackup)
    factoryOf(::ImportBackup)
    factoryOf(::CheckForUpdate)
}
