package dev.catsradar.domain.update

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val version: AppVersion, val apk: ReleasePackage) : UpdateCheck
    data class Failed(val reason: FeedFailure) : UpdateCheck
}
