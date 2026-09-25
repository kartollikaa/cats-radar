package dev.catsradar.presentation.settings

data class UpdateState(val status: UpdateStatus = UpdateStatus.Idle) {
    val checkEnabled: Boolean get() = status != UpdateStatus.Checking
}

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val version: String) : UpdateStatus
    data class Failed(val reason: UpdateFailure) : UpdateStatus
}

/** Why a check found nothing to say about versions. The words live in `:ui`. */
enum class UpdateFailure {
    OFFLINE,
    SOURCE_UNAVAILABLE,
    UNREADABLE_ANSWER,
}
