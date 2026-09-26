package dev.catsradar.presentation.settings

import dev.catsradar.domain.update.FeedFailure
import dev.catsradar.domain.update.UpdateCheck

private const val PERCENT = 100

class UpdateStateMapper {

    fun checking(): UpdateState = UpdateState(UpdateStatus.Checking, UpdateAction.Busy)

    fun map(check: UpdateCheck): UpdateState = when (check) {
        UpdateCheck.UpToDate -> UpdateState(UpdateStatus.UpToDate)
        is UpdateCheck.Available -> downloading(check.version.toString(), fraction = null)
        is UpdateCheck.Failed -> UpdateState(UpdateStatus.Failed(check.reason.toToken()))
    }

    // Rounded down, so a download never reads 100 % before it has ended.
    fun downloading(version: String, fraction: Float?): UpdateState = UpdateState(
        status = fraction
            ?.let { UpdateStatus.Downloading(version, (it * PERCENT).toInt().coerceIn(0, PERCENT)) }
            ?: UpdateStatus.DownloadStarting(version),
        action = UpdateAction.Busy,
    )

    fun ready(version: String): UpdateState =
        UpdateState(UpdateStatus.ReadyToInstall(version), UpdateAction.Install(version))

    fun needsInstallPermission(version: String): UpdateState =
        UpdateState(UpdateStatus.NeedsInstallPermission(version), UpdateAction.AllowInstalls)

    fun installing(version: String): UpdateState = UpdateState(UpdateStatus.Installing(version), UpdateAction.Busy)

    // A new check downloads the package again: offering the same one could fail the same way forever.
    fun installFailed(version: String, outcome: InstallOutcome): UpdateState =
        UpdateState(UpdateStatus.InstallFailed(version, outcome.toFailure()))

    fun downloadFailed(): UpdateState = UpdateState(UpdateStatus.Failed(UpdateFailure.DOWNLOAD_FAILED))

    private fun InstallOutcome.toFailure(): InstallFailure = when (this) {
        InstallOutcome.CONFLICT -> InstallFailure.SIGNED_DIFFERENTLY
        InstallOutcome.INCOMPATIBLE -> InstallFailure.INCOMPATIBLE
        InstallOutcome.STORAGE -> InstallFailure.NO_SPACE
        InstallOutcome.MISSING_PACKAGE -> InstallFailure.PACKAGE_GONE
        InstallOutcome.NOT_THIS_APP -> InstallFailure.NOT_THIS_APP
        InstallOutcome.NOT_NEWER -> InstallFailure.NOT_NEWER
        InstallOutcome.CANCELLED, InstallOutcome.FAILED -> InstallFailure.OTHER
    }

    private fun FeedFailure.toToken(): UpdateFailure = when (this) {
        FeedFailure.OFFLINE -> UpdateFailure.OFFLINE
        FeedFailure.UNAVAILABLE -> UpdateFailure.SOURCE_UNAVAILABLE
        FeedFailure.UNREADABLE -> UpdateFailure.UNREADABLE_ANSWER
    }
}
