package dev.catsradar.domain.usecase

import dev.catsradar.domain.platform.DownloadedPackage
import dev.catsradar.domain.platform.PackageDownloader
import dev.catsradar.domain.update.DownloadFailure
import dev.catsradar.domain.update.DownloadResult
import dev.catsradar.domain.update.ReleasePackage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadUpdateTest {

    private val digest = "e4eebd3ae6e095ecc92df3f4859b03edf14bc799dd6e4cb4f61c629882cbbdf1"
    private val apk =
        ReleasePackage("https://example.test/cats-radar-1.5.0-beta.apk", sizeBytes = 1_000, sha256 = digest)

    private class FakeDownloader(private val written: DownloadedPackage?) : PackageDownloader {
        val discarded = mutableListOf<String>()
        var fileName: String? = null

        override suspend fun download(url: String, fileName: String, onProgress: suspend (Long) -> Unit) =
            written.also {
                this.fileName = fileName
                onProgress(it?.sizeBytes ?: 0)
            }

        override suspend fun kept(): List<String> = emptyList()

        override suspend fun discard(path: String) {
            discarded += path
        }
    }

    @Test
    fun `a package of the published size and digest is ready to install`() = runTest {
        val downloader = FakeDownloader(DownloadedPackage("/cache/updates/1.5.0-beta.apk", 1_000, digest))

        val result = DownloadUpdate(downloader)("1.5.0-beta", apk) {}

        assertEquals(DownloadResult.Downloaded("/cache/updates/1.5.0-beta.apk"), result)
        assertEquals("1.5.0-beta.apk", downloader.fileName)
        assertEquals(emptyList(), downloader.discarded)
    }

    @Test
    fun `a package whose digest differs is thrown away as damaged`() = runTest {
        val downloader = FakeDownloader(DownloadedPackage("/cache/updates/1.5.0-beta.apk", 1_000, "00".repeat(32)))

        val result = DownloadUpdate(downloader)("1.5.0-beta", apk) {}

        assertEquals(DownloadResult.Failed(DownloadFailure.DAMAGED), result)
        assertEquals(listOf("/cache/updates/1.5.0-beta.apk"), downloader.discarded)
    }

    @Test
    fun `a package whose size differs is thrown away as damaged`() = runTest {
        val downloader = FakeDownloader(DownloadedPackage("/cache/updates/1.5.0-beta.apk", 999, digest))

        val result = DownloadUpdate(downloader)("1.5.0-beta", apk) {}

        assertEquals(DownloadResult.Failed(DownloadFailure.DAMAGED), result)
        assertEquals(listOf("/cache/updates/1.5.0-beta.apk"), downloader.discarded)
    }

    @Test
    fun `an asset published without a digest is checked on its size alone`() = runTest {
        val downloader = FakeDownloader(DownloadedPackage("/cache/updates/1.5.0-beta.apk", 1_000, "ab".repeat(32)))

        val result = DownloadUpdate(downloader)("1.5.0-beta", apk.copy(sha256 = null)) {}

        assertEquals(DownloadResult.Downloaded("/cache/updates/1.5.0-beta.apk"), result)
    }

    @Test
    fun `an asset published without a digest is still thrown away when its size differs`() = runTest {
        val downloader = FakeDownloader(DownloadedPackage("/cache/updates/1.5.0-beta.apk", 999, "ab".repeat(32)))

        val result = DownloadUpdate(downloader)("1.5.0-beta", apk.copy(sha256 = null)) {}

        assertEquals(DownloadResult.Failed(DownloadFailure.DAMAGED), result)
        assertEquals(listOf("/cache/updates/1.5.0-beta.apk"), downloader.discarded)
    }

    @Test
    fun `a download that broke off is a network failure`() = runTest {
        assertEquals(
            DownloadResult.Failed(DownloadFailure.NETWORK),
            DownloadUpdate(FakeDownloader(written = null))("1.5.0-beta", apk) {},
        )
    }

    @Test
    fun `progress is passed on as it arrives`() = runTest {
        val received = mutableListOf<Long>()

        DownloadUpdate(FakeDownloader(DownloadedPackage("/p", 1_000, digest)))("1.5.0-beta", apk) { received += it }

        assertEquals(listOf(1_000L), received)
    }
}
