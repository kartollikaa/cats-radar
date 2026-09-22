package dev.catsradar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.settings.SettingsState
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
fun SettingsScreen(
    state: SettingsState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onSaveOriginalsChange: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.padding(end = 16.dp)) {
                Text(
                    text = stringResource(R.string.settings_save_originals),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_save_originals_explained),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = state.saveOriginalsToGallery, onCheckedChange = onSaveOriginalsChange)
        }
    }
}

@ThemePreviews
@Composable
private fun SettingsScreenPreview() {
    CatsRadarTheme {
        Surface { SettingsScreen(state = SettingsState(saveOriginalsToGallery = true)) }
    }
}
