package dev.catsradar.presentation.settings

sealed interface SettingsEffect {
    /** The notification itself belongs to the platform layer; the store only says when. */
    data class WalkingMode(val enabled: Boolean) : SettingsEffect

    /** Asks the screen to open the system file picker for a new archive to write. */
    data object PickExportTarget : SettingsEffect

    data object PickImportSource : SettingsEffect
    data class StartExport(val target: String) : SettingsEffect
    data class StartImport(val source: String) : SettingsEffect
}
