package dev.catsradar.presentation.settings

sealed interface SettingsIntent {
    data class SaveOriginalsToggled(val enabled: Boolean) : SettingsIntent
}
