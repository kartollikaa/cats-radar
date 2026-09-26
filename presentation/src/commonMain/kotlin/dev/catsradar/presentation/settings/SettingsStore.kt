package dev.catsradar.presentation.settings

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.platform.BuildInfoReader
import dev.catsradar.domain.platform.Feature
import dev.catsradar.domain.platform.FeatureToggles
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
    private val updates: SettingsUpdates,
    featureToggles: FeatureToggles,
) : Store<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    private val backupRun = ReportedRun(settingsRepository, ReportedJob.BACKUP)

    private val updateScreen = object : UpdateScreen {
        override val update: UpdateState get() = state.value.update

        override fun show(update: UpdateState) = setState { copy(update = update) }

        override suspend fun send(effect: SettingsEffect) = emit(effect)
    }

    init {
        settingsRepository.saveOriginalsToGallery()
            .onEach { enabled -> setState { copy(saveOriginalsToGallery = enabled) } }
            .launchIn(viewModelScope)
        settingsRepository.encountersGrid()
            .onEach { enabled -> setState { copy(encountersGrid = enabled) } }
            .launchIn(viewModelScope)
        featureToggles.isOn(Feature.IN_APP_UPDATES)
            .onEach { on -> setState { copy(updatesShown = on) } }
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
            is SettingsIntent.Update -> updates.handle(intent, updateScreen)
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
