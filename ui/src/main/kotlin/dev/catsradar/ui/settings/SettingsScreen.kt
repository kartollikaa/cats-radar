package dev.catsradar.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.settings.AboutState
import dev.catsradar.presentation.settings.BackupOutcome
import dev.catsradar.presentation.settings.SettingsState
import dev.catsradar.presentation.settings.UpdateAction
import dev.catsradar.presentation.settings.UpdateFailure
import dev.catsradar.presentation.settings.UpdateState
import dev.catsradar.presentation.settings.UpdateStatus
import dev.catsradar.ui.R
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.components.ValueRow
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
fun SettingsScreen(
    state: SettingsState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onSaveOriginalsChange: (Boolean) -> Unit = {},
    onEncountersGridChange: (Boolean) -> Unit = {},
    onExportClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onBackupOutcomeDismiss: () -> Unit = {},
    onCheckForUpdatesClick: () -> Unit = {},
    onInstallUpdateClick: () -> Unit = {},
    onAllowInstallsClick: () -> Unit = {},
    onCopyBuildInfoClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SectionCard(R.string.settings_section_logging) {
            SettingRow(
                title = R.string.settings_save_originals,
                explanation = R.string.settings_save_originals_explained,
                checked = state.saveOriginalsToGallery,
                onCheckedChange = onSaveOriginalsChange,
            )
        }
        SectionCard(R.string.tab_encounters) {
            SettingRow(
                title = R.string.settings_encounters_grid,
                explanation = R.string.settings_encounters_grid_explained,
                checked = state.encountersGrid,
                onCheckedChange = onEncountersGridChange,
            )
        }
        SectionCard(R.string.settings_backup) {
            BackupSection(
                state = state,
                onExportClick = onExportClick,
                onImportClick = onImportClick,
                onBackupOutcomeDismiss = onBackupOutcomeDismiss,
            )
        }
        SectionCard(R.string.settings_updates) {
            UpdatesSection(
                update = state.update,
                onCheckClick = onCheckForUpdatesClick,
                onInstallClick = onInstallUpdateClick,
                onAllowInstallsClick = onAllowInstallsClick,
            )
        }
        state.about?.let { about -> AboutSection(about = about, onCopyClick = onCopyBuildInfoClick) }
    }
}

@Composable
private fun UpdatesSection(
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
    is UpdateStatus.InstallFailed -> stringResource(R.string.settings_updates_install_failed, version)
    is UpdateStatus.Failed -> stringResource(
        when (reason) {
            UpdateFailure.OFFLINE -> R.string.settings_updates_offline
            UpdateFailure.SOURCE_UNAVAILABLE -> R.string.settings_updates_source_unavailable
            UpdateFailure.UNREADABLE_ANSWER -> R.string.settings_updates_unreadable
            UpdateFailure.DOWNLOAD_FAILED -> R.string.settings_updates_download_failed
        },
    )
}

@Composable
private fun AboutSection(about: AboutState, modifier: Modifier = Modifier, onCopyClick: () -> Unit = {}) {
    SectionCard(
        titleRes = R.string.settings_about,
        modifier = modifier,
        action = {
            IconButton(onClick = onCopyClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.settings_about_copy),
                )
            }
        },
    ) {
        ValueRow(label = stringResource(R.string.settings_about_version), value = about.version)
        ValueRow(label = stringResource(R.string.settings_about_build), value = about.build)
        ValueRow(label = stringResource(R.string.settings_about_device), value = about.device)
        ValueRow(
            label = stringResource(R.string.settings_about_android),
            value = stringResource(R.string.settings_about_android_value, about.androidRelease, about.sdkInt),
        )
    }
}

@Composable
private fun BackupSection(
    state: SettingsState,
    modifier: Modifier = Modifier,
    onExportClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onBackupOutcomeDismiss: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_backup_explained),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onExportClick, enabled = !state.backupRunning) {
                Text(text = stringResource(R.string.settings_export))
            }
            OutlinedButton(onClick = onImportClick, enabled = !state.backupRunning) {
                Text(text = stringResource(R.string.settings_import))
            }
        }
        if (state.backupRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.backupOutcome?.let { outcome ->
            BackupOutcomeRow(outcome = outcome, onDismiss = onBackupOutcomeDismiss)
        }
    }
}

@Composable
private fun BackupOutcomeRow(outcome: BackupOutcome, modifier: Modifier = Modifier, onDismiss: () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(outcome.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.settings_backup_ok))
        }
    }
}

@Composable
private fun SettingRow(
    @StringRes title: Int,
    @StringRes explanation: Int,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@StringRes
private fun BackupOutcome.messageRes(): Int = when (this) {
    BackupOutcome.EXPORTED -> R.string.settings_backup_exported
    BackupOutcome.EXPORT_FAILED -> R.string.settings_backup_export_failed
    BackupOutcome.IMPORTED -> R.string.settings_backup_imported
    BackupOutcome.IMPORT_REFUSED_TOO_NEW -> R.string.settings_backup_too_new
    BackupOutcome.IMPORT_REFUSED_UNREADABLE -> R.string.settings_backup_unreadable
    BackupOutcome.IMPORT_FAILED -> R.string.settings_backup_import_failed
}

@ThemePreviews
@Composable
private fun SettingsScreenPreview() {
    CatsRadarTheme {
        Surface { SettingsScreen(state = SettingsState(saveOriginalsToGallery = true, about = sampleAbout)) }
    }
}

@ThemePreviews
@Composable
private fun SettingsScreenImportFailedPreview() {
    CatsRadarTheme {
        Surface { SettingsScreen(state = SettingsState(backupOutcome = BackupOutcome.IMPORT_FAILED)) }
    }
}

private val sampleAbout = AboutState(
    version = "1.4.1-beta (7)",
    build = "release · 5989a92c1f3e",
    device = "Google Pixel 7",
    androidRelease = "16",
    sdkInt = 36,
)
