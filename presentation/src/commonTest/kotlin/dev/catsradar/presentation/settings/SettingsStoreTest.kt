package dev.catsradar.presentation.settings

import app.cash.turbine.test
import dev.catsradar.domain.about.BuildInfo
import dev.catsradar.domain.platform.BuildInfoReader
import dev.catsradar.domain.platform.Feature
import dev.catsradar.domain.platform.FeatureToggles
import dev.catsradar.domain.platform.InstallPermission
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
        installPermission: InstallPermission = InstallPermission { true },
        updatesFlag: Flow<Boolean> = flowOf(true),
    ) = SettingsStore(
        settingsRepository = repository,
        buildInfoReader = buildInfoReader,
        aboutStateMapper = AboutStateMapper(),
        updates = SettingsUpdates(
            checkForUpdate = CheckForUpdate(updateSource, pixelBuildInfo.app),
            installed = pixelBuildInfo.app,
            installPermission = installPermission,
            mapper = UpdateStateMapper(),
        ),
        featureToggles = object : FeatureToggles {
            override fun isOn(feature: Feature): Flow<Boolean> =
                if (feature == Feature.IN_APP_UPDATES) updatesFlag else flowOf(false)
        },
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

    private val newerApk = ReleasePackage("https://x/cats-radar-1.5.0-beta.apk", 13_682_980, null)
    private val newerFeed = UpdateSource {
        ReleaseFeed.Listed(listOf(PublishedRelease("v1.5.0-beta", newerApk)))
    }
    private val downloaded = SettingsIntent.Update.DownloadFinished(
        "run-1",
        "1.5.0-beta",
        "/cache/updates/1.5.0-beta.apk"
    )

    @Test
    fun `before any check the updates section offers one`() = runTest(mainDispatcher) {
        val store = newStore()
        runCurrent()

        assertEquals(UpdateState(UpdateStatus.Idle, UpdateAction.Check), store.state.value.update)
    }

    @Test
    fun `a check in progress shows as checking, with the button taken away`() = runTest(mainDispatcher) {
        val answer = CompletableDeferred<ReleaseFeed>()
        val store = settingsStore(updateSource = UpdateSource { answer.await() })

        store.dispatch(SettingsIntent.Update.CheckClicked)
        runCurrent()

        assertEquals(UpdateState(UpdateStatus.Checking, UpdateAction.Busy), store.state.value.update)
    }

    @Test
    fun `a newer release starts downloading at once`() = runTest(mainDispatcher) {
        val store = settingsStore(updateSource = newerFeed)
        store.effects.test {
            store.dispatch(SettingsIntent.Update.CheckClicked)
            runCurrent()

            assertEquals(SettingsEffect.StartUpdateDownload("1.5.0-beta", newerApk), awaitItem())
            assertEquals(
                UpdateState(UpdateStatus.DownloadStarting("1.5.0-beta"), UpdateAction.Busy),
                store.state.value.update,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the installed release is reported as up to date`() = runTest(mainDispatcher) {
        val same = PublishedRelease("v1.4.1-beta", ReleasePackage("https://x/a.apk", 1, null))
        val store = settingsStore(updateSource = UpdateSource { ReleaseFeed.Listed(listOf(same)) })

        store.dispatch(SettingsIntent.Update.CheckClicked)
        runCurrent()

        assertEquals(UpdateState(UpdateStatus.UpToDate, UpdateAction.Check), store.state.value.update)
    }

    @Test
    fun `a failed check says why and offers another`() = runTest(mainDispatcher) {
        val store = settingsStore(updateSource = UpdateSource { ReleaseFeed.Failed(FeedFailure.OFFLINE) })

        store.dispatch(SettingsIntent.Update.CheckClicked)
        runCurrent()

        assertEquals(
            UpdateState(UpdateStatus.Failed(UpdateFailure.OFFLINE), UpdateAction.Check),
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

        store.dispatch(SettingsIntent.Update.CheckClicked)
        store.dispatch(SettingsIntent.Update.CheckClicked)
        runCurrent()
        answer.complete(ReleaseFeed.Listed(emptyList()))
        runCurrent()

        assertEquals(1, asked)
    }

    @Test
    fun `a tap while a download runs starts no second one`() = runTest(mainDispatcher) {
        val store = settingsStore(updateSource = newerFeed)
        store.effects.test {
            store.dispatch(SettingsIntent.Update.CheckClicked)
            runCurrent()
            awaitItem()

            store.dispatch(SettingsIntent.Update.CheckClicked)
            runCurrent()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `download progress shows as a percentage`() = runTest(mainDispatcher) {
        val store = newStore()

        store.dispatch(SettingsIntent.Update.DownloadProgressed("1.5.0-beta", 0.45f))
        runCurrent()

        assertEquals(UpdateStatus.Downloading("1.5.0-beta", percent = 45), store.state.value.update.status)
    }

    @Test
    fun `a download this screen started installs as soon as it finishes`() = runTest(mainDispatcher) {
        val store = settingsStore(updateSource = newerFeed)
        store.effects.test {
            store.dispatch(SettingsIntent.Update.CheckClicked)
            runCurrent()
            awaitItem()

            store.dispatch(downloaded)
            runCurrent()

            assertEquals(SettingsEffect.InstallUpdate("/cache/updates/1.5.0-beta.apk"), awaitItem())
            assertEquals(
                UpdateState(UpdateStatus.Installing("1.5.0-beta"), UpdateAction.Busy),
                store.state.value.update,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a download found finished by a newly opened screen waits for a tap`() = runTest(mainDispatcher) {
        val store = newStore()
        store.effects.test {
            store.dispatch(downloaded)
            runCurrent()

            expectNoEvents()
            assertEquals(
                UpdateState(UpdateStatus.ReadyToInstall("1.5.0-beta"), UpdateAction.Install("1.5.0-beta")),
                store.state.value.update,
            )

            store.dispatch(SettingsIntent.Update.InstallClicked)
            runCurrent()

            assertEquals(SettingsEffect.InstallUpdate("/cache/updates/1.5.0-beta.apk"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a finished download reported again is not installed twice`() = runTest(mainDispatcher) {
        val store = settingsStore(updateSource = newerFeed)
        store.effects.test {
            store.dispatch(SettingsIntent.Update.CheckClicked)
            runCurrent()
            awaitItem()

            store.dispatch(downloaded)
            store.dispatch(downloaded)
            runCurrent()

            awaitItem()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a package of the installed version is not offered`() = runTest(mainDispatcher) {
        val store = newStore()

        store.dispatch(SettingsIntent.Update.DownloadFinished("run-0", "1.4.1-beta", "/cache/updates/1.4.1-beta.apk"))
        runCurrent()

        assertEquals(UpdateState(UpdateStatus.Idle, UpdateAction.Check), store.state.value.update)
    }

    @Test
    fun `a cancelled install goes back to offering it`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(downloaded)
        store.dispatch(SettingsIntent.Update.InstallClicked)
        runCurrent()

        store.dispatch(SettingsIntent.Update.InstallFinished(InstallOutcome.CANCELLED))
        runCurrent()

        assertEquals(
            UpdateState(UpdateStatus.ReadyToInstall("1.5.0-beta"), UpdateAction.Install("1.5.0-beta")),
            store.state.value.update,
        )
    }

    @Test
    fun `a failed install says so and offers a new check`() = runTest(mainDispatcher) {
        val store = newStore()
        store.dispatch(downloaded)
        store.dispatch(SettingsIntent.Update.InstallClicked)
        runCurrent()

        store.dispatch(SettingsIntent.Update.InstallFinished(InstallOutcome.CONFLICT))
        runCurrent()

        assertEquals(
            UpdateState(UpdateStatus.InstallFailed("1.5.0-beta", InstallFailure.SIGNED_DIFFERENTLY)),
            store.state.value.update,
        )
    }

    @Test
    fun `a download that fails while it is shown says so and offers a new check`() = runTest(mainDispatcher) {
        val store = settingsStore(updateSource = newerFeed)
        store.dispatch(SettingsIntent.Update.CheckClicked)
        runCurrent()

        store.dispatch(SettingsIntent.Update.DownloadFailed("run-1"))
        runCurrent()

        assertEquals(
            UpdateState(UpdateStatus.Failed(UpdateFailure.DOWNLOAD_FAILED), UpdateAction.Check),
            store.state.value.update,
        )
    }

    @Test
    fun `an old failed download read back by a newly opened screen shows nothing`() = runTest(mainDispatcher) {
        val store = newStore()

        store.dispatch(SettingsIntent.Update.DownloadFailed("run-0"))
        runCurrent()

        assertEquals(UpdateState(UpdateStatus.Idle, UpdateAction.Check), store.state.value.update)
    }

    @Test
    fun `an install without the permission opens its system page instead`() = runTest(mainDispatcher) {
        val store = settingsStore(installPermission = InstallPermission { false })
        store.effects.test {
            store.dispatch(downloaded)
            store.dispatch(SettingsIntent.Update.InstallClicked)
            runCurrent()

            assertEquals(SettingsEffect.OpenInstallPermission, awaitItem())
            expectNoEvents()
            assertEquals(
                UpdateState(UpdateStatus.NeedsInstallPermission("1.5.0-beta"), UpdateAction.AllowInstalls),
                store.state.value.update,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a download this screen started opens the page as soon as it finishes`() = runTest(mainDispatcher) {
        val store = settingsStore(updateSource = newerFeed, installPermission = InstallPermission { false })
        store.effects.test {
            store.dispatch(SettingsIntent.Update.CheckClicked)
            runCurrent()
            awaitItem()

            store.dispatch(downloaded)
            runCurrent()

            assertEquals(SettingsEffect.OpenInstallPermission, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `coming back with the permission installs without another tap`() = runTest(mainDispatcher) {
        var allowed = false
        val store = settingsStore(installPermission = InstallPermission { allowed })
        store.effects.test {
            store.dispatch(downloaded)
            store.dispatch(SettingsIntent.Update.InstallClicked)
            runCurrent()
            awaitItem()

            allowed = true
            store.dispatch(SettingsIntent.Update.InstallPermissionReturned)
            runCurrent()

            assertEquals(SettingsEffect.InstallUpdate("/cache/updates/1.5.0-beta.apk"), awaitItem())
            assertEquals(
                UpdateState(UpdateStatus.Installing("1.5.0-beta"), UpdateAction.Busy),
                store.state.value.update,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `coming back without the permission offers its page again`() = runTest(mainDispatcher) {
        val store = settingsStore(installPermission = InstallPermission { false })
        store.effects.test {
            store.dispatch(downloaded)
            store.dispatch(SettingsIntent.Update.InstallClicked)
            runCurrent()
            awaitItem()

            store.dispatch(SettingsIntent.Update.InstallPermissionReturned)
            runCurrent()
            expectNoEvents()
            assertEquals(
                UpdateState(UpdateStatus.NeedsInstallPermission("1.5.0-beta"), UpdateAction.AllowInstalls),
                store.state.value.update,
            )

            store.dispatch(SettingsIntent.Update.AllowInstallsClicked)
            runCurrent()

            assertEquals(SettingsEffect.OpenInstallPermission, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the updates section is hidden while its flag is off`() = runTest(mainDispatcher) {
        val store = settingsStore(updatesFlag = flowOf(false))
        runCurrent()

        assertEquals(false, store.state.value.updatesShown)
    }

    @Test
    fun `the updates section shows while its flag is on`() = runTest(mainDispatcher) {
        val store = settingsStore(updatesFlag = flowOf(true))
        runCurrent()

        assertEquals(true, store.state.value.updatesShown)
    }

    @Test
    fun `a flag that changes while the screen is open shows or hides the section`() = runTest(mainDispatcher) {
        val flag = MutableStateFlow(false)
        val store = settingsStore(updatesFlag = flag)
        runCurrent()
        val shown = mutableListOf(store.state.value.updatesShown)

        flag.value = true
        runCurrent()
        shown += store.state.value.updatesShown
        flag.value = false
        runCurrent()
        shown += store.state.value.updatesShown

        assertEquals(listOf(false, true, false), shown)
    }
}
