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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.settings.BackupOutcome
import dev.catsradar.presentation.settings.SettingsState
import dev.catsradar.ui.R
import dev.catsradar.ui.components.SectionCard
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
fun SettingsScreen(
    state: SettingsState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onSaveOriginalsChange: (Boolean) -> Unit = {},
    onWalkingModeChange: (Boolean) -> Unit = {},
    onEncountersGridChange: (Boolean) -> Unit = {},
    onExportClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onBackupOutcomeDismiss: () -> Unit = {},
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
            SettingRow(
                title = R.string.settings_walking,
                explanation = R.string.settings_walking_explained,
                checked = state.walkingMode,
                onCheckedChange = onWalkingModeChange,
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
        Surface { SettingsScreen(state = SettingsState(saveOriginalsToGallery = true)) }
    }
}

@ThemePreviews
@Composable
private fun SettingsScreenImportFailedPreview() {
    CatsRadarTheme {
        Surface { SettingsScreen(state = SettingsState(backupOutcome = BackupOutcome.IMPORT_FAILED)) }
    }
}
