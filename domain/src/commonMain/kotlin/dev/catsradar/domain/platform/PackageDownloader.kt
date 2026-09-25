package dev.catsradar.domain.platform

data class DownloadedPackage(val path: String, val sizeBytes: Long, val sha256: String)

interface PackageDownloader {
    /**
     * Writes [url] as [fileName], replacing any package kept before, and reports the bytes received
     * so far. Null when the download broke off; nothing is left behind then.
     */
    suspend fun download(url: String, fileName: String, onProgress: suspend (Long) -> Unit): DownloadedPackage?

    /** Paths of the packages kept from earlier downloads. */
    suspend fun kept(): List<String>

    suspend fun discard(path: String)
}
