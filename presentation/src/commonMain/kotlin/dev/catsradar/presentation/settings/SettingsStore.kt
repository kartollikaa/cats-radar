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
    }

    override suspend fun handle(intent: SettingsIntent) {
        when (intent) {
            // The stored value is the source of truth: the switch follows the flow above rather
            // than its own optimistic state, so a failed write cannot leave them disagreeing.
            is SettingsIntent.SaveOriginalsToggled ->
                settingsRepository.setSaveOriginalsToGallery(intent.enabled)
        }
    }
}
