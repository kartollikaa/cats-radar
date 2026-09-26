package dev.catsradar.domain.usecase

import dev.catsradar.domain.about.InstalledApp
import dev.catsradar.domain.platform.PackageDownloader
import dev.catsradar.domain.update.isOlderThan

/**
 * Deletes kept packages that are no longer an update: the one just installed and older ones. A file that
 * is not a package yet is a download in progress, which may be what started this process.
 */
class PruneInstalledUpdates(
    private val downloader: PackageDownloader,
    private val installed: InstalledApp,
) {
    suspend operator fun invoke() {
        downloader.kept()
            .filter { path -> DownloadUpdate.versionOf(path)?.let(installed::isOlderThan) == false }
            .forEach { path -> downloader.discard(path) }
    }
}
