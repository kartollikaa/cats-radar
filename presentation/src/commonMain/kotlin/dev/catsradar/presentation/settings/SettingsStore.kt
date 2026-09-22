package dev.catsradar.presentation.settings

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.repository.SettingsRepository
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SettingsStore(
    private val settingsRepository: SettingsRepository,
) : Store<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    init {
        settingsRepository.saveOriginalsToGallery()
            .onEach { enabled -> setState { copy(saveOriginalsToGallery = enabled) } }
            .launchIn(viewModelScope)
        settingsRepository.walkingMode()
            .onEach { enabled -> setState { copy(walkingMode = enabled) } }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: SettingsIntent) {
        when (intent) {
            // The stored value is the source of truth: the switch follows the flow above rather
            // than its own optimistic state, so a failed write cannot leave them disagreeing.
            is SettingsIntent.SaveOriginalsToggled ->
                settingsRepository.setSaveOriginalsToGallery(intent.enabled)
            is SettingsIntent.WalkingModeToggled -> {
                settingsRepository.setWalkingMode(intent.enabled)
                emit(SettingsEffect.WalkingMode(intent.enabled))
            }
            is SettingsIntent.Backup -> handleBackup(intent)
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
            is SettingsIntent.Backup.Finished ->
                setState { copy(backupRunning = false, backupOutcome = intent.outcome) }
            SettingsIntent.Backup.OutcomeDismissed -> setState { copy(backupOutcome = null) }
        }
    }
}
