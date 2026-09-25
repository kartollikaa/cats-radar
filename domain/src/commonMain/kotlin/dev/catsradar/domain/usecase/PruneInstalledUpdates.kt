package dev.catsradar.domain.usecase

import dev.catsradar.domain.about.InstalledApp
import dev.catsradar.domain.platform.PackageDownloader
import dev.catsradar.domain.update.isOlderThan

/** Deletes kept packages that are no longer an update: the one just installed, older ones, half-written ones. */
class PruneInstalledUpdates(
    private val downloader: PackageDownloader,
    private val installed: InstalledApp,
) {
    suspend operator fun invoke() {
        downloader.kept()
            .filterNot { path -> DownloadUpdate.versionOf(path)?.let(installed::isOlderThan) == true }
            .forEach { path -> downloader.discard(path) }
    }
}
