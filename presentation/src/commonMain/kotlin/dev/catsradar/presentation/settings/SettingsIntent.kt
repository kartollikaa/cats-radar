package dev.catsradar.presentation.settings

sealed interface SettingsIntent {
    data class SaveOriginalsToggled(val enabled: Boolean) : SettingsIntent
    data class EncountersGridToggled(val enabled: Boolean) : SettingsIntent

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
