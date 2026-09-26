package dev.catsradar.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.settings.InstallFailure
import dev.catsradar.presentation.settings.UpdateAction
import dev.catsradar.presentation.settings.UpdateFailure
import dev.catsradar.presentation.settings.UpdateState
import dev.catsradar.presentation.settings.UpdateStatus
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun UpdatesSection(
    update: UpdateState,
    modifier: Modifier = Modifier,
    onCheckClick: () -> Unit = {},
    onInstallClick: () -> Unit = {},
    onAllowInstallsClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = update.status.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val status = update.status) {
            UpdateStatus.Checking, is UpdateStatus.DownloadStarting, is UpdateStatus.Installing ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is UpdateStatus.Downloading ->
                LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth())
            else -> Unit
        }
        when (val action = update.action) {
            UpdateAction.Check, UpdateAction.Busy -> Button(
                onClick = onCheckClick,
                enabled = action == UpdateAction.Check,
            ) {
                Text(text = stringResource(R.string.settings_updates_check))
            }
            is UpdateAction.Install -> Button(onClick = onInstallClick) {
                Text(text = stringResource(R.string.settings_updates_install, action.version))
            }
            UpdateAction.AllowInstalls -> Button(onClick = onAllowInstallsClick) {
                Text(text = stringResource(R.string.settings_updates_allow_installs))
            }
        }
    }
}

@Composable
private fun UpdateStatus.message(): String = when (this) {
    UpdateStatus.Idle, UpdateStatus.Checking -> stringResource(R.string.settings_updates_explained)
    UpdateStatus.UpToDate -> stringResource(R.string.settings_updates_up_to_date)
    is UpdateStatus.DownloadStarting -> stringResource(R.string.settings_updates_downloading, version)
    is UpdateStatus.Downloading -> stringResource(R.string.settings_updates_downloading_percent, version, percent)
    is UpdateStatus.ReadyToInstall -> stringResource(R.string.settings_updates_ready, version)
    is UpdateStatus.NeedsInstallPermission -> stringResource(R.string.settings_updates_needs_permission, version)
    is UpdateStatus.Installing -> stringResource(R.string.settings_updates_installing, version)
    is UpdateStatus.InstallFailed -> stringResource(reason.messageRes(), version)
    is UpdateStatus.Failed -> stringResource(reason.messageRes())
}

@StringRes
private fun InstallFailure.messageRes(): Int = when (this) {
    InstallFailure.SIGNED_DIFFERENTLY -> R.string.settings_updates_install_conflict
    InstallFailure.INCOMPATIBLE -> R.string.settings_updates_install_incompatible
    InstallFailure.NO_SPACE -> R.string.settings_updates_install_storage
    InstallFailure.PACKAGE_GONE -> R.string.settings_updates_install_package_gone
    InstallFailure.NOT_THIS_APP -> R.string.settings_updates_install_not_this_app
    InstallFailure.NOT_NEWER -> R.string.settings_updates_install_not_newer
    InstallFailure.OTHER -> R.string.settings_updates_install_failed
}

@StringRes
private fun UpdateFailure.messageRes(): Int = when (this) {
    UpdateFailure.OFFLINE -> R.string.settings_updates_offline
    UpdateFailure.SOURCE_UNAVAILABLE -> R.string.settings_updates_source_unavailable
    UpdateFailure.UNREADABLE_ANSWER -> R.string.settings_updates_unreadable
    UpdateFailure.DOWNLOAD_FAILED -> R.string.settings_updates_download_failed
}

@ThemePreviews
@Composable
private fun UpdatesSectionPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            UpdatesSection(update = UpdateState(UpdateStatus.Downloading("1.5.0-beta", 45), UpdateAction.Busy))
            UpdatesSection(
                update = UpdateState(UpdateStatus.NeedsInstallPermission("1.5.0-beta"), UpdateAction.AllowInstalls),
            )
            UpdatesSection(
                update = UpdateState(UpdateStatus.InstallFailed("1.5.0-beta", InstallFailure.SIGNED_DIFFERENTLY)),
            )
        }
    }
}
