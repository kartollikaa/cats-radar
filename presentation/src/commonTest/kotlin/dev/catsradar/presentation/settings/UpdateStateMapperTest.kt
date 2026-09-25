package dev.catsradar.presentation.settings

import dev.catsradar.domain.update.AppVersion
import dev.catsradar.domain.update.FeedFailure
import dev.catsradar.domain.update.ReleasePackage
import dev.catsradar.domain.update.UpdateCheck
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateStateMapperTest {

    private val mapper = UpdateStateMapper()

    @Test
    fun `nothing checked yet offers a check`() {
        assertEquals(UpdateState(UpdateStatus.Idle, UpdateAction.Check), UpdateState())
    }

    @Test
    fun `a check in progress takes the button away`() {
        assertEquals(UpdateState(UpdateStatus.Checking, UpdateAction.Busy), mapper.checking())
    }

    @Test
    fun `an available release starts downloading, its size not known yet`() {
        val check = UpdateCheck.Available(AppVersion.parse("v1.5.0-beta")!!, ReleasePackage("https://x/a.apk", 1, null))

        assertEquals(
            UpdateState(UpdateStatus.DownloadStarting("1.5.0-beta"), UpdateAction.Busy),
            mapper.map(check)
        )
    }

    @Test
    fun `a download in progress shows whole percents, never 100 before it ends`() {
        assertEquals(
            listOf(0, 45, 99).map { UpdateState(UpdateStatus.Downloading("1.5.0-beta", it), UpdateAction.Busy) },
            listOf(0f, 0.456f, 0.999f).map { mapper.downloading("1.5.0-beta", it) },
        )
    }

    @Test
    fun `a download whose size is not known yet is starting`() {
        assertEquals(
            UpdateState(UpdateStatus.DownloadStarting("1.5.0-beta"), UpdateAction.Busy),
            mapper.downloading("1.5.0-beta", fraction = null),
        )
    }

    @Test
    fun `a downloaded package offers to install its version`() {
        assertEquals(
            UpdateState(UpdateStatus.ReadyToInstall("1.5.0-beta"), UpdateAction.Install("1.5.0-beta")),
            mapper.ready("1.5.0-beta"),
        )
    }

    @Test
    fun `an install under way takes the button away`() {
        assertEquals(
            UpdateState(UpdateStatus.Installing("1.5.0-beta"), UpdateAction.Busy),
            mapper.installing("1.5.0-beta")
        )
    }

    @Test
    fun `a failed install offers a new check, which downloads the package afresh`() {
        assertEquals(
            UpdateState(UpdateStatus.InstallFailed("1.5.0-beta"), UpdateAction.Check),
            mapper.installFailed("1.5.0-beta"),
        )
    }

    @Test
    fun `a failed download offers a new check`() {
        assertEquals(
            UpdateState(UpdateStatus.Failed(UpdateFailure.DOWNLOAD_FAILED), UpdateAction.Check),
            mapper.downloadFailed()
        )
    }

    @Test
    fun `an up-to-date app says so`() {
        assertEquals(UpdateState(UpdateStatus.UpToDate, UpdateAction.Check), mapper.map(UpdateCheck.UpToDate))
    }

    @Test
    fun `each way a check or a download fails has its own message, and none share one`() {
        val tokens = FeedFailure.entries.map { reason ->
            (mapper.map(UpdateCheck.Failed(reason)).status as UpdateStatus.Failed).reason
        } + (mapper.downloadFailed().status as UpdateStatus.Failed).reason

        assertEquals(
            listOf(
                UpdateFailure.OFFLINE,
                UpdateFailure.SOURCE_UNAVAILABLE,
                UpdateFailure.UNREADABLE_ANSWER,
                UpdateFailure.DOWNLOAD_FAILED,
            ),
            tokens,
        )
        assertEquals(tokens.size, tokens.toSet().size)
    }

    @Test
    fun `an install waiting for the permission offers its page`() {
        assertEquals(
            UpdateState(UpdateStatus.NeedsInstallPermission("1.5.0-beta"), UpdateAction.AllowInstalls),
            mapper.needsInstallPermission("1.5.0-beta"),
        )
    }
}
