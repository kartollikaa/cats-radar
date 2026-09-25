package dev.catsradar.presentation.settings

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.platform.BuildInfoReader
import dev.catsradar.domain.repository.ReportedJob
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.presentation.ReportedRun
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsStore(
    private val settingsRepository: SettingsRepository,
    private val buildInfoReader: BuildInfoReader,
    private val aboutStateMapper: AboutStateMapper,
) : Store<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    private val backupRun = ReportedRun(settingsRepository, ReportedJob.BACKUP)

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
            SettingsIntent.BuildInfoCopyClicked ->
                state.value.about?.let { emit(SettingsEffect.CopyBuildInfo(it.report)) }
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
