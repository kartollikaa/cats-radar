package dev.catsradar.presentation.settings

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.about.InstalledApp
import dev.catsradar.domain.platform.BuildInfoReader
import dev.catsradar.domain.platform.InstallPermission
import dev.catsradar.domain.repository.ReportedJob
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.domain.update.UpdateCheck
import dev.catsradar.domain.update.isOlderThan
import dev.catsradar.domain.usecase.CheckForUpdate
import dev.catsradar.presentation.ReportedRun
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsStore(
    private val settingsRepository: SettingsRepository,
    private val buildInfoReader: BuildInfoReader,
    private val aboutStateMapper: AboutStateMapper,
    private val checkForUpdate: CheckForUpdate,
    private val updateStateMapper: UpdateStateMapper,
    private val installed: InstalledApp,
    private val installPermission: InstallPermission,
) : Store<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    private val backupRun = ReportedRun(settingsRepository, ReportedJob.BACKUP)

    // Only a download this screen asked for installs by itself; one found finished waits for a tap.
    private var installWhenDownloaded = false
    private var downloadedPath: String? = null
    private var handledDownloadRun: String? = null

    init {
        settingsRepository.saveOriginalsToGallery()
            .onEach { enabled -> setState { copy(saveOriginalsToGallery = enabled) } }
            .launchIn(viewModelScope)
        settingsRepository.encountersGrid()
            .onEach { enabled -> setState { copy(encountersGrid = enabled) } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            val about = aboutStateMapper.map(buildInfoReader.read())
            setState { copy(about = about) }
        }
    }

    override suspend fun handle(intent: SettingsIntent) {
        when (intent) {
            // The stored value is the source of truth: the switch follows the flow above rather
            // than its own optimistic state, so a failed write cannot leave them disagreeing.
            is SettingsIntent.SaveOriginalsToggled ->
                settingsRepository.setSaveOriginalsToGallery(intent.enabled)
            is SettingsIntent.EncountersGridToggled -> settingsRepository.setEncountersGrid(intent.enabled)
            is SettingsIntent.Backup -> handleBackup(intent)
            // Read again rather than kept: the locale or the zone may have changed since the screen opened.
            SettingsIntent.BuildInfoCopyClicked ->
                emit(SettingsEffect.CopyBuildInfo(aboutStateMapper.report(buildInfoReader.read())))
            is SettingsIntent.Update -> handleUpdate(intent)
        }
    }

    private suspend fun handleUpdate(intent: SettingsIntent.Update) {
        when (intent) {
            SettingsIntent.Update.CheckClicked -> checkForUpdates()
            SettingsIntent.Update.InstallClicked -> installDownloaded()
            SettingsIntent.Update.AllowInstallsClicked -> emit(SettingsEffect.OpenInstallPermission)
            SettingsIntent.Update.InstallPermissionReturned -> permissionReturned()
            is SettingsIntent.Update.DownloadProgressed -> if (downloadShowable(intent.version)) {
                setState { copy(update = updateStateMapper.downloading(intent.version, intent.fraction)) }
            }
            is SettingsIntent.Update.DownloadFinished -> downloadFinished(intent)
            is SettingsIntent.Update.DownloadFailed -> downloadFailed(intent.runId)
            is SettingsIntent.Update.InstallFinished -> installFinished(intent.outcome)
        }
    }

    private suspend fun checkForUpdates() {
        if (state.value.update.action != UpdateAction.Check) return
        setState { copy(update = updateStateMapper.checking()) }
        val result = checkForUpdate()
        setState { copy(update = updateStateMapper.map(result)) }
        if (result is UpdateCheck.Available) {
            installWhenDownloaded = true
            emit(SettingsEffect.StartUpdateDownload(result.version.toString(), result.apk))
        }
    }

    private fun downloadShowable(version: String): Boolean = installed.isOlderThan(version) &&
        state.value.update.status.let { it !is UpdateStatus.ReadyToInstall && it !is UpdateStatus.Installing }

    private suspend fun downloadFinished(finished: SettingsIntent.Update.DownloadFinished) {
        if (finished.runId == handledDownloadRun) return
        handledDownloadRun = finished.runId
        if (!installed.isOlderThan(finished.version)) return
        downloadedPath = finished.path
        if (installWhenDownloaded) {
            installWhenDownloaded = false
            install(finished.version, finished.path)
        } else {
            setState { copy(update = updateStateMapper.ready(finished.version)) }
        }
    }

    private fun downloadFailed(runId: String) {
        if (runId == handledDownloadRun) return
        handledDownloadRun = runId
        val status = state.value.update.status
        if (status !is UpdateStatus.Downloading && status !is UpdateStatus.DownloadStarting) return
        installWhenDownloaded = false
        setState { copy(update = updateStateMapper.downloadFailed()) }
    }

    private suspend fun installDownloaded() {
        val action = state.value.update.action as? UpdateAction.Install ?: return
        downloadedPath?.let { install(action.version, it) }
    }

    // Asked before every install: without it Android shows a refusal instead of its confirmation.
    private suspend fun install(version: String, path: String) {
        if (!installPermission.granted()) {
            setState { copy(update = updateStateMapper.needsInstallPermission(version)) }
            emit(SettingsEffect.OpenInstallPermission)
            return
        }
        setState { copy(update = updateStateMapper.installing(version)) }
        emit(SettingsEffect.InstallUpdate(path))
    }

    private suspend fun permissionReturned() {
        val waiting = state.value.update.status as? UpdateStatus.NeedsInstallPermission ?: return
        val path = downloadedPath ?: return
        if (installPermission.granted()) install(waiting.version, path)
    }

    private fun installFinished(outcome: InstallOutcome) {
        val version = (state.value.update.status as? UpdateStatus.Installing)?.version ?: return
        setState {
            copy(
                update = when (outcome) {
                    InstallOutcome.CANCELLED -> updateStateMapper.ready(version)
                    InstallOutcome.FAILED -> updateStateMapper.installFailed(version)
                },
            )
        }
    }

    private suspend fun handleBackup(intent: SettingsIntent.Backup) {
        when (intent) {
            SettingsIntent.Backup.ExportRequested -> emit(SettingsEffect.PickExportTarget)
            SettingsIntent.Backup.ImportRequested -> emit(SettingsEffect.PickImportSource)
            // A dismissed picker is not a run: nothing starts, and the last outcome stays on screen.
            is SettingsIntent.Backup.ExportTargetChosen ->
                intent.uri?.let { emit(SettingsEffect.StartExport(it)) }
            is SettingsIntent.Backup.ImportSourceChosen ->
                intent.uri?.let { emit(SettingsEffect.StartImport(it)) }
            SettingsIntent.Backup.Started ->
                setState { copy(backupRunning = true, backupOutcome = null) }
            is SettingsIntent.Backup.Finished -> if (backupRun.claim(intent.runId)) {
                setState { copy(backupRunning = false, backupOutcome = intent.outcome) }
            }
            SettingsIntent.Backup.OutcomeDismissed -> {
                setState { copy(backupOutcome = null) }
                backupRun.acknowledge()
            }
        }
    }
}
