package dev.catsradar.presentation.counter

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.usecase.LogPhoto
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveOpenWalk
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.ObserveWalkElapsed
import dev.catsradar.domain.usecase.SetCoat
import dev.catsradar.domain.usecase.UndoImport
import dev.catsradar.domain.usecase.UndoLastTally
import dev.catsradar.presentation.NoAnalytics
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
import dev.catsradar.presentation.encounters.FakePhotoStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

internal val CounterNow: Instant = Instant.parse("2026-09-22T10:00:00Z")

// A test about undo or photos is not also a test about celebrating the first cat; the milestone
// tests pass their own starting point.
internal fun milestonesAlreadyCelebrated() = FakeSettingsRepository(lastMilestone = Tuning.MILESTONES.last())

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList") // a fixture builder: one parameter per collaborator a test replaces
internal fun TestScope.newCounterStore(
    encounterRepository: FakeEncounterRepository = FakeEncounterRepository(),
    locationPermissionRequestState: FakeLocationPermissionRequestState = FakeLocationPermissionRequestState(),
    settingsRepository: FakeSettingsRepository = milestonesAlreadyCelebrated(),
    exifReader: FakeExifReader = FakeExifReader(),
    imageResizer: FakeImageResizer = FakeImageResizer(),
    ticks: Flow<Unit> = flowOf(Unit),
    walkRepository: FakeWalkRepository = FakeWalkRepository(),
): CounterStore {
    val clock = FakeClock(CounterNow)
    val idGenerator = FakeIdGenerator()
    val store = CounterStore(
        logTally = LogTally(
            encounterRepository,
            idGenerator,
            FakeDeviceIdProvider(),
            clock,
            analytics = NoAnalytics,
            TimeZone.UTC
        ),
        logPhoto = LogPhoto(
            encounterRepository = encounterRepository,
            placeCellRepository = FakePlaceCellRepository(),
            settingsRepository = settingsRepository,
            exifReader = exifReader,
            imageResizer = imageResizer,
            digest = FakeDigest(),
            gallerySaver = FakeGallerySaver(),
            idGenerator = idGenerator,
            deviceIdProvider = FakeDeviceIdProvider(),
            clock = clock,
            timeZone = TimeZone.UTC,
            analytics = NoAnalytics,
        ),
        undoLastTally = UndoLastTally(encounterRepository, clock, analytics = NoAnalytics),
        undoImport = UndoImport(encounterRepository, clock, analytics = NoAnalytics),
        setCoat = SetCoat(encounterRepository, clock, analytics = NoAnalytics),
        observeStats = ObserveStats(encounterRepository, clock, TimeZone.UTC, ticks = ticks),
        observeWalkElapsed = ObserveWalkElapsed(ObserveOpenWalk(walkRepository), clock, ticks = ticks),
        settingsRepository = settingsRepository,
        stateMapper = CounterStateMapper(FakeDateTimeFormatter(), FakePhotoStorage()),
        locationPermissionRequestState = locationPermissionRequestState,
    )
    runCurrent()
    return store
}
