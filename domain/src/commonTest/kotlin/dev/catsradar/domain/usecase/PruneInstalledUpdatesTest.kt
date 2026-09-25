package dev.catsradar.domain.usecase

import dev.catsradar.domain.about.InstalledApp
import dev.catsradar.domain.platform.DownloadedPackage
import dev.catsradar.domain.platform.PackageDownloader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PruneInstalledUpdatesTest {

    private val installed = InstalledApp(
        versionName = "1.4.2-beta",
        versionCode = 8,
        buildType = "release",
        applicationId = "com.kartollika.catsradar",
        commit = "5989a92c1f3e",
        databaseVersion = 4,
    )

    private class Kept(private val paths: List<String>) : PackageDownloader {
        val discarded = mutableListOf<String>()

        override suspend fun download(url: String, fileName: String, onProgress: suspend (Long) -> Unit):
            DownloadedPackage? = null

        override suspend fun kept(): List<String> = paths

        override suspend fun discard(path: String) {
            discarded += path
        }
    }

    @Test
    fun `the package of the version now installed, and any older one, is deleted`() = runTest {
        val kept = Kept(listOf("/cache/updates/1.4.2-beta.apk", "/cache/updates/1.4.1-beta.apk"))

        PruneInstalledUpdates(kept, installed)()

        assertEquals(listOf("/cache/updates/1.4.2-beta.apk", "/cache/updates/1.4.1-beta.apk"), kept.discarded)
    }

    @Test
    fun `a package newer than the installed version is kept for its install`() = runTest {
        val kept = Kept(listOf("/cache/updates/1.5.0-beta.apk"))

        PruneInstalledUpdates(kept, installed)()

        assertEquals(emptyList(), kept.discarded)
    }

    @Test
    fun `a half-written file is left to the download writing it`() = runTest {
        val kept = Kept(listOf("/cache/updates/1.5.0-beta.apk.part"))

        PruneInstalledUpdates(kept, installed)()

        assertEquals(emptyList(), kept.discarded)
    }
}
