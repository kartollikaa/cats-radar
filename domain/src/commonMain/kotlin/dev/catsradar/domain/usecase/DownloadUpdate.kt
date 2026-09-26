package dev.catsradar.domain.usecase

import dev.catsradar.domain.platform.PackageDownloader
import dev.catsradar.domain.update.DownloadFailure
import dev.catsradar.domain.update.DownloadResult
import dev.catsradar.domain.update.ReleasePackage

class DownloadUpdate(private val downloader: PackageDownloader) {

    /** A truncated or swapped file is deleted here, so it never reaches the installer. */
    suspend operator fun invoke(
        version: String,
        apk: ReleasePackage,
        onProgress: suspend (Long) -> Unit,
    ): DownloadResult {
        val written = downloader.download(apk.url, fileName(version), onProgress)
            ?: return DownloadResult.Failed(DownloadFailure.NETWORK)
        val intact = written.sizeBytes == apk.sizeBytes &&
            (apk.sha256 == null || apk.sha256.equals(written.sha256, ignoreCase = true))
        return if (intact) {
            DownloadResult.Downloaded(written.path)
        } else {
            downloader.discard(written.path)
            DownloadResult.Failed(DownloadFailure.DAMAGED)
        }
    }

    internal companion object {
        private const val EXTENSION = ".apk"

        fun fileName(version: String) = version + EXTENSION

        /** The version a kept package was downloaded as; null for any other file. */
        fun versionOf(path: String): String? =
            path.substringAfterLast('/').takeIf { it.endsWith(EXTENSION) }?.removeSuffix(EXTENSION)
    }
}
