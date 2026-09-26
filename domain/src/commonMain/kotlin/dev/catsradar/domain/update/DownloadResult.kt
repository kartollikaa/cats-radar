package dev.catsradar.domain.update

sealed interface DownloadResult {
    data class Downloaded(val path: String) : DownloadResult
    data class Failed(val reason: DownloadFailure) : DownloadResult
}

enum class DownloadFailure {
    NETWORK,

    /** The file arrived, but not the one that was published: a different size or digest. */
    DAMAGED,
}
