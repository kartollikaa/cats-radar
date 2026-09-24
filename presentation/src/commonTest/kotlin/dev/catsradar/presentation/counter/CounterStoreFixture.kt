package dev.catsradar.presentation.counter

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.usecase.LogPhoto
import dev.catsradar.domain.usecase.LogTally
import dev.catsradar.domain.usecase.ObserveStats
import dev.catsradar.domain.usecase.ObserveWalkElapsed
import dev.catsradar.domain.usecase.UndoImport
import dev.catsradar.domain.usecase.UndoLastTally
import dev.catsradar.presentation.encounters.FakeDateTimeFormatter
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
    val store = CounterStore(
        logTally = LogTally(encounterRepository, FakeIdGenerator(), FakeDeviceIdProvider(), clock, TimeZone.UTC),
        logPhoto = LogPhoto(
            encounterRepository = encounterRepository,
            placeCellRepository = FakePlaceCellRepository(),
            settingsRepository = settingsRepository,
            exifReader = exifReader,
            imageResizer = imageResizer,
            digest = FakeDigest(),
            gallerySaver = FakeGallerySaver(),
            idGenerator = FakeIdGenerator(),
            deviceIdProvider = FakeDeviceIdProvider(),
            clock = clock,
            timeZone = TimeZone.UTC,
        ),
        undoLastTally = UndoLastTally(encounterRepository, clock),
        undoImport = UndoImport(encounterRepository, clock),
        observeStats = ObserveStats(encounterRepository, clock, TimeZone.UTC, ticks = ticks),
        observeWalkElapsed = ObserveWalkElapsed(walkRepository, clock, ticks = ticks),
        settingsRepository = settingsRepository,
        stateMapper = CounterStateMapper(FakeDateTimeFormatter()),
        locationPermissionRequestState = locationPermissionRequestState,
    )
    runCurrent()
    return store
}
