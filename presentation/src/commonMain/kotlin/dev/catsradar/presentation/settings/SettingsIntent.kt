package dev.catsradar.presentation.settings

sealed interface SettingsIntent {
    data class SaveOriginalsToggled(val enabled: Boolean) : SettingsIntent
    data class EncountersGridToggled(val enabled: Boolean) : SettingsIntent
    data object BuildInfoCopyClicked : SettingsIntent

    /** Updating the app: a sub-flow of Settings. */
    sealed interface Update : SettingsIntent {
        data object CheckClicked : Update
        data object InstallClicked : Update
        data object AllowInstallsClicked : Update

        /** Back from the system page for installing apps, whatever the user chose there. */
        data object InstallPermissionReturned : Update

        /** [fraction] is null while the size is not known yet. */
        data class DownloadProgressed(val version: String, val fraction: Float?) : Update

        /** The same run may be reported again; [runId] tells a repeat from a new run. */
        data class DownloadFinished(val runId: String, val version: String, val path: String) : Update
        data class DownloadFailed(val runId: String) : Update
        data class InstallFinished(val outcome: InstallOutcome) : Update
    }

    /** Backing up: a sub-flow of Settings, not a screen of its own. */
    sealed interface Backup : SettingsIntent {
        data object ExportRequested : Backup
        data object ImportRequested : Backup

        /** [uri] is null when the file picker was dismissed without choosing anything. */
        data class ExportTargetChosen(val uri: String?) : Backup
        data class ImportSourceChosen(val uri: String?) : Backup
        data object Started : Backup

        /** The same run may be reported again; [runId] tells a repeat from a new run. */
        data class Finished(val runId: String, val outcome: BackupOutcome) : Backup
        data object OutcomeDismissed : Backup
    }
}
