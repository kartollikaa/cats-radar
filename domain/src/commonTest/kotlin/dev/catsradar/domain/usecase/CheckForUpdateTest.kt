package dev.catsradar.domain.usecase

import dev.catsradar.domain.about.InstalledApp
import dev.catsradar.domain.platform.UpdateSource
import dev.catsradar.domain.update.AppVersion
import dev.catsradar.domain.update.FeedFailure
import dev.catsradar.domain.update.PublishedRelease
import dev.catsradar.domain.update.ReleaseFeed
import dev.catsradar.domain.update.ReleasePackage
import dev.catsradar.domain.update.UpdateCheck
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckForUpdateTest {

    private val installed = InstalledApp(
        versionName = "1.4.1-beta",
        versionCode = 7,
        buildType = "release",
        applicationId = "com.kartollika.catsradar",
        commit = "5989a92c1f3e",
        databaseVersion = 3,
    )

    private fun apk(tag: String) =
        ReleasePackage("https://example.test/$tag.apk", sizeBytes = 13_682_980, sha256 = null)

    private fun release(tag: String) = PublishedRelease(tag, apk(tag))

    private suspend fun check(vararg releases: PublishedRelease): UpdateCheck =
        CheckForUpdate(UpdateSource { ReleaseFeed.Listed(releases.toList()) }, installed)()

    @Test
    fun `the newest release above the installed version is offered`() = runTest {
        assertEquals(
            UpdateCheck.Available(AppVersion.parse("1.5.0-beta")!!, apk("v1.5.0-beta")),
            check(release("v1.4.1-beta"), release("v1.5.0-beta"), release("v1.4.2-beta")),
        )
    }

    @Test
    fun `the installed version itself is up to date`() = runTest {
        assertEquals(UpdateCheck.UpToDate, check(release("v1.4.1-beta"), release("v1.4.0-beta")))
    }

    @Test
    fun `an older newest release is up to date`() = runTest {
        assertEquals(UpdateCheck.UpToDate, check(release("v1.4.0-beta")))
    }

    @Test
    fun `no release at all is up to date`() = runTest {
        assertEquals(UpdateCheck.UpToDate, check())
    }

    @Test
    fun `a newest release without a package is passed over for the next one`() = runTest {
        assertEquals(
            UpdateCheck.Available(AppVersion.parse("1.5.0-beta")!!, apk("v1.5.0-beta")),
            check(PublishedRelease("v1.6.0-beta", apk = null), release("v1.5.0-beta")),
        )
    }

    @Test
    fun `a tag that is not a version is passed over`() = runTest {
        assertEquals(UpdateCheck.UpToDate, check(release("nightly"), release("v1.4.1-beta")))
    }

    @Test
    fun `a pre-release is offered like a release`() = runTest {
        assertEquals(
            UpdateCheck.Available(AppVersion.parse("1.4.1-rc")!!, apk("v1.4.1-rc")),
            check(release("v1.4.1-rc")),
        )
    }

    @Test
    fun `an installed version that is not a version is offered the newest release`() = runTest {
        val local = CheckForUpdate(
            UpdateSource { ReleaseFeed.Listed(listOf(release("v1.4.1-beta"))) },
            installed.copy(versionName = "local"),
        )

        assertEquals(UpdateCheck.Available(AppVersion.parse("1.4.1-beta")!!, apk("v1.4.1-beta")), local())
    }

    @Test
    fun `a feed that fails says why`() = runTest {
        val result = CheckForUpdate(UpdateSource { ReleaseFeed.Failed(FeedFailure.UNAVAILABLE) }, installed)()

        assertEquals(UpdateCheck.Failed(FeedFailure.UNAVAILABLE), result)
    }
}
