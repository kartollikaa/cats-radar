package dev.catsradar.domain.usecase

import dev.catsradar.domain.about.InstalledApp
import dev.catsradar.domain.platform.UpdateSource
import dev.catsradar.domain.update.AppVersion
import dev.catsradar.domain.update.ReleaseFeed
import dev.catsradar.domain.update.UpdateCheck

class CheckForUpdate(
    private val updateSource: UpdateSource,
    private val installed: InstalledApp,
) {
    /** The newest release with a package, when it is newer than the installed version. */
    suspend operator fun invoke(): UpdateCheck = when (val feed = updateSource.releases()) {
        is ReleaseFeed.Failed -> UpdateCheck.Failed(feed.reason)
        is ReleaseFeed.Listed -> {
            val newest = feed.releases
                .mapNotNull { release ->
                    val version = AppVersion.parse(release.tag) ?: return@mapNotNull null
                    val apk = release.apk ?: return@mapNotNull null
                    UpdateCheck.Available(version, apk)
                }
                .maxByOrNull { it.version }
            val current = AppVersion.parse(installed.versionName)
            if (newest != null && (current == null || newest.version > current)) newest else UpdateCheck.UpToDate
        }
    }
}
