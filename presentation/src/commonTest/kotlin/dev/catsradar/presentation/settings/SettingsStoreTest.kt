package dev.catsradar.presentation.settings

import app.cash.turbine.test
import dev.catsradar.domain.about.BuildInfo
import dev.catsradar.domain.platform.BuildInfoReader
import dev.catsradar.domain.platform.UpdateSource
import dev.catsradar.domain.repository.ReportedJob
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.update.FeedFailure
import dev.catsradar.domain.update.PublishedRelease
import dev.catsradar.domain.update.ReleaseFeed
import dev.catsradar.domain.update.ReleasePackage
import dev.catsradar.domain.usecase.CheckForUpdate
import dev.catsradar.presentation.counter.FakeSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun newStore() = settingsStore()

    private fun settingsStore(
        repository: SettingsRepository = FakeSettingsRepository(),
        buildInfoReader: BuildInfoReader = BuildInfoReader { pixelBuildInfo },
        updateSource: UpdateSource = UpdateSource { ReleaseFeed.Listed(emptyList()) },
    ) = SettingsStore(
        settingsRepository = repository,
        buildInfoReader = buildInfoReader,
        aboutStateMapper = AboutStateMapper(),
        checkForUpdate = CheckForUpdate(updateSource, pixelBuildInfo.app),
        updateStateMapper = UpdateStateMapper(),
    )

    @Test
    fun `asking to export opens the file picker and starts nothing yet`() = runTest(mainDispatcher) {
        val store = newStore()
        store.effects.test {
            store.dispatch(SettingsIntent.Backup.ExportRequested)
            runCurrent()

            assertEquals(SettingsEffect.PickExportTarget, awaitItem())
            assertEquals(false, store.state.value.backupRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a target starts the export`() = runTest(mainDispatcher) {
        val store = newStore()
        store.effects.test {
            store.dispatch(SettingsIntent.Backup.ExportTargetChosen("content://out.zip"))
            runCurrent()

            assertEquals(SettingsEffect.StartExport("content://out.zip"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the file picker starts nothing`() = runTest(mainDispatcher) {
        val store = newStore()
        store.effects.test {
            store.dispatch(SettingsIntent.Backup.ExportTargetChosen(null))
            store.dispatch(SettingsIntent.Backup.ImportSourceChosen(null))
            runCurrent()

            // Asserted on the effects, not only the state: a run started with an empty uri would
            // leave the state alone and still hand a worker a file it cannot open.
            expectNoEvents()
            assertEquals(false, store.state.value.backupRunning)
            assertNull(store.state.value.backupOutcome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a run in progress takes both buttons away and clears the last outcome`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.EXPORTED))
        runCurrent()

        store.dispatch(SettingsIntent.Backup.Started)
        runCurrent()

        assertEquals(true, store.state.value.backupRunning)
        assertNull(store.state.value.backupOutcome)
    }

    @Test
    fun `a finished run gives the buttons back and says what happened`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(SettingsIntent.Backup.Started)
        runCurrent()

        store.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.IMPORT_REFUSED_TOO_NEW))
        runCurrent()

        assertEquals(false, store.state.value.backupRunning)
        assertEquals(BackupOutcome.IMPORT_REFUSED_TOO_NEW, store.state.value.backupOutcome)
    }

    @Test
    fun `dismissing the outcome leaves the buttons available`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.EXPORTED))
        runCurrent()

        store.dispatch(SettingsIntent.Backup.OutcomeDismissed)
        runCurrent()

        assertNull(store.state.value.backupOutcome)
        assertEquals(false, store.state.value.backupRunning)
    }

    @Test
    fun `a dismissed outcome stays gone when its run is read back`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.EXPORTED))
        runCurrent()
        store.dispatch(SettingsIntent.Backup.OutcomeDismissed)
        runCurrent()

        store.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.EXPORTED))
        runCurrent()

        assertNull(store.state.value.backupOutcome)
    }

    @Test
    fun `a dismissed outcome is not shown by a new screen reading its run back`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository()
        val first = settingsStore(repository)
        first.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.EXPORTED))
        runCurrent()
        first.dispatch(SettingsIntent.Backup.OutcomeDismissed)
        runCurrent()

        val second = settingsStore(repository)
        second.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.EXPORTED))
        runCurrent()

        assertNull(second.state.value.backupOutcome)
    }

    @Test
    fun `an outcome nobody dismissed is shown again by a new screen`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository()
        val first = settingsStore(repository)
        first.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.IMPORTED))
        runCurrent()

        val second = settingsStore(repository)
        second.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.IMPORTED))
        runCurrent()

        assertEquals(BackupOutcome.IMPORTED, second.state.value.backupOutcome)
    }

    @Test
    fun `a newer run's outcome is shown after an earlier one was dismissed`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.EXPORTED))
        runCurrent()
        store.dispatch(SettingsIntent.Backup.OutcomeDismissed)
        runCurrent()

        store.dispatch(SettingsIntent.Backup.Finished("run-2", BackupOutcome.EXPORT_FAILED))
        runCurrent()

        assertEquals(BackupOutcome.EXPORT_FAILED, store.state.value.backupOutcome)
    }

    @Test
    fun `dismissing while a newer run is being checked records the run that was on screen`() =
        runTest(mainDispatcher) {
            val repository = FakeSettingsRepository()
            val store = settingsStore(repository)
            store.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.EXPORTED))
            runCurrent()
            val gate = CompletableDeferred<Unit>()
            repository.acknowledgedRunReadGate = gate

            store.dispatch(SettingsIntent.Backup.Finished("run-2", BackupOutcome.IMPORTED))
            runCurrent()
            store.dispatch(SettingsIntent.Backup.OutcomeDismissed)
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertEquals("run-1", repository.acknowledgedRun(ReportedJob.BACKUP).first())
            assertEquals(BackupOutcome.IMPORTED, store.state.value.backupOutcome)
        }

    @Test
    fun `a dismissed run read back again does not end the run in progress`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.EXPORTED))
        runCurrent()
        store.dispatch(SettingsIntent.Backup.OutcomeDismissed)
        store.dispatch(SettingsIntent.Backup.Started)
        runCurrent()

        store.dispatch(SettingsIntent.Backup.Finished("run-1", BackupOutcome.EXPORTED))
        runCurrent()

        assertEquals(true, store.state.value.backupRunning)
        assertNull(store.state.value.backupOutcome)
    }

    @Test
    fun `the gallery switch still follows the stored value, not the tap`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository(saveOriginals = true)
        val store = settingsStore(repository)
        runCurrent()

        store.dispatch(SettingsIntent.SaveOriginalsToggled(false))
        runCurrent()

        assertEquals(false, store.state.value.saveOriginalsToGallery)
    }

    @Test
    fun `the grid switch shows the stored value`() = runTest(mainDispatcher) {
        val store = settingsStore(FakeSettingsRepository(encountersGrid = false))
        runCurrent()

        assertEquals(false, store.state.value.encountersGrid)
    }

    @Test
    fun `turning the grid switch off stores it, and the switch follows the stored value`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository(encountersGrid = true)
        val store = settingsStore(repository)
        runCurrent()

        store.dispatch(SettingsIntent.EncountersGridToggled(false))
        runCurrent()

        assertEquals(false, repository.encountersGrid().first())
        assertEquals(false, store.state.value.encountersGrid)
    }

    @Test
    fun `turning the grid switch back on stores it`() = runTest(mainDispatcher) {
        val repository = FakeSettingsRepository(encountersGrid = false)
        val store = settingsStore(repository)
        runCurrent()

        store.dispatch(SettingsIntent.EncountersGridToggled(true))
        runCurrent()

        assertEquals(true, repository.encountersGrid().first())
        assertEquals(true, store.state.value.encountersGrid)
    }

    @Test
    fun `the about section shows the build it was read from`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()

        assertEquals(AboutStateMapper().map(pixelBuildInfo), store.state.value.about)
    }

    @Test
    fun `copying the build info hands its report to the screen`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()
        store.effects.test {
            store.dispatch(SettingsIntent.BuildInfoCopyClicked)
            runCurrent()

            assertEquals(SettingsEffect.CopyBuildInfo(AboutStateMapper().report(pixelBuildInfo)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a copy reports the locale the phone has now, not the one it had when the screen opened`() =
        runTest(mainDispatcher) {
            var current = pixelBuildInfo
            val store = settingsStore(buildInfoReader = BuildInfoReader { current })
            runCurrent()
            current = pixelBuildInfo.copy(
                device = pixelBuildInfo.device.copy(localeTag = "en-GB", timeZoneId = "Europe/London"),
            )
            store.effects.test {
                store.dispatch(SettingsIntent.BuildInfoCopyClicked)
                runCurrent()

                assertEquals(SettingsEffect.CopyBuildInfo(AboutStateMapper().report(current)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a copy reports the locale and zone the phone has at the tap, even while the screen's own read is pending`() =
        runTest(mainDispatcher) {
            val firstRead = CompletableDeferred<BuildInfo>()
            var reads = 0
            val now = pixelBuildInfo.copy(
                device = pixelBuildInfo.device.copy(localeTag = "en-GB", timeZoneId = "Europe/London"),
            )
            val store = settingsStore(
                buildInfoReader = BuildInfoReader { if (reads++ == 0) firstRead.await() else now },
            )
            runCurrent()
            store.effects.test {
                store.dispatch(SettingsIntent.BuildInfoCopyClicked)
                runCurrent()

                assertEquals(SettingsEffect.CopyBuildInfo(AboutStateMapper().report(now)), awaitItem())
                assertNull(store.state.value.about)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `before any check the updates section offers one`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()

        assertEquals(UpdateState(UpdateStatus.Idle, checkEnabled = true), store.state.value.update)
    }

    @Test
    fun `a check in progress shows as checking, with the button taken away`() = runTest(mainDispatcher) {
        val answer = CompletableDeferred<ReleaseFeed>()
        val store = settingsStore(updateSource = UpdateSource { answer.await() })

        store.dispatch(SettingsIntent.UpdateCheckClicked)
        runCurrent()

        assertEquals(UpdateState(UpdateStatus.Checking, checkEnabled = false), store.state.value.update)
    }

    @Test
    fun `a newer release is reported as available`() = runTest(mainDispatcher) {
        val newer = PublishedRelease("v1.5.0-beta", ReleasePackage("https://x/a.apk", 1, null))
        val store = settingsStore(updateSource = UpdateSource { ReleaseFeed.Listed(listOf(newer)) })

        store.dispatch(SettingsIntent.UpdateCheckClicked)
        runCurrent()

        assertEquals(UpdateState(UpdateStatus.Available("1.5.0-beta"), checkEnabled = true), store.state.value.update)
    }

    @Test
    fun `the installed release is reported as up to date`() = runTest(mainDispatcher) {
        val same = PublishedRelease("v1.4.1-beta", ReleasePackage("https://x/a.apk", 1, null))
        val store = settingsStore(updateSource = UpdateSource { ReleaseFeed.Listed(listOf(same)) })

        store.dispatch(SettingsIntent.UpdateCheckClicked)
        runCurrent()

        assertEquals(UpdateState(UpdateStatus.UpToDate, checkEnabled = true), store.state.value.update)
    }

    @Test
    fun `a failed check says why and offers another`() = runTest(mainDispatcher) {
        val store = settingsStore(updateSource = UpdateSource { ReleaseFeed.Failed(FeedFailure.OFFLINE) })

        store.dispatch(SettingsIntent.UpdateCheckClicked)
        runCurrent()

        assertEquals(
            UpdateState(UpdateStatus.Failed(UpdateFailure.OFFLINE), checkEnabled = true),
            store.state.value.update,
        )
    }

    @Test
    fun `a second tap while a check runs asks the source nothing more`() = runTest(mainDispatcher) {
        val answer = CompletableDeferred<ReleaseFeed>()
        var asked = 0
        val counting = UpdateSource {
            asked++
            answer.await()
        }
        val store = settingsStore(updateSource = counting)

        store.dispatch(SettingsIntent.UpdateCheckClicked)
        store.dispatch(SettingsIntent.UpdateCheckClicked)
        runCurrent()
        answer.complete(ReleaseFeed.Listed(emptyList()))
        runCurrent()

        assertEquals(1, asked)
    }
}
