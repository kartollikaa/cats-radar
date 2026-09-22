package dev.catsradar.presentation.settings

data class SettingsState(
    val saveOriginalsToGallery: Boolean = true,
    /** True while an export or import is running; neither button is available meanwhile. */
    val backupRunning: Boolean = false,
    /** Null until a run finishes, and again once its message is dismissed. */
    val backupOutcome: BackupOutcome? = null,
)

/** What to tell the user about the run that just ended. The words live in `:ui`. */
enum class BackupOutcome {
    EXPORTED,
    EXPORT_FAILED,
    IMPORTED,
    IMPORT_REFUSED_TOO_NEW,
    IMPORT_REFUSED_UNREADABLE,
}
