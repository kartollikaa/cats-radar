package dev.catsradar.presentation.settings

import dev.catsradar.domain.update.ReleasePackage

sealed interface SettingsEffect {
    /** Asks the screen to open the system file picker for a new archive to write. */
    data object PickExportTarget : SettingsEffect

    data object PickImportSource : SettingsEffect
    data class StartExport(val target: String) : SettingsEffect
    data class StartImport(val source: String) : SettingsEffect
    data class CopyBuildInfo(val report: String) : SettingsEffect
    data class StartUpdateDownload(val version: String, val apk: ReleasePackage) : SettingsEffect
    data class InstallUpdate(val path: String) : SettingsEffect
}
