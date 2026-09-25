package dev.catsradar.presentation.settings

data class UpdateState(
    val status: UpdateStatus = UpdateStatus.Idle,
    val action: UpdateAction = UpdateAction.Check,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus

    /** Waiting for a network, or for the first bytes that tell the size. */
    data class DownloadStarting(val version: String) : UpdateStatus
    data class Downloading(val version: String, val percent: Int) : UpdateStatus
    data class ReadyToInstall(val version: String) : UpdateStatus
    data class NeedsInstallPermission(val version: String) : UpdateStatus
    data class Installing(val version: String) : UpdateStatus
    data class InstallFailed(val version: String) : UpdateStatus
    data class Failed(val reason: UpdateFailure) : UpdateStatus
}

/** The one button under the status. */
sealed interface UpdateAction {
    data object Check : UpdateAction

    /** Check, unavailable while something runs. */
    data object Busy : UpdateAction
    data class Install(val version: String) : UpdateAction

    /** Opens the system page where the user lets this app install packages. */
    data object AllowInstalls : UpdateAction
}

/** Why there is nothing to install. The words live in `:ui`. */
enum class UpdateFailure {
    OFFLINE,
    SOURCE_UNAVAILABLE,
    UNREADABLE_ANSWER,
    DOWNLOAD_FAILED,
}
