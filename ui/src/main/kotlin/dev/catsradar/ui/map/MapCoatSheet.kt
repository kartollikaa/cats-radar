package dev.catsradar.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatGrid
import dev.catsradar.ui.components.CatsRadarBottomSheet
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@Composable
internal fun MapCoatSheet(
    shown: ImmutableSet<CoatOption?>,
    modifier: Modifier = Modifier,
    onCoatToggle: (CoatOption?) -> Unit = {},
    onClear: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    CatsRadarBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        MapCoatContent(shown = shown, onCoatToggle = onCoatToggle, onClear = onClear)
    }
}

@Composable
private fun MapCoatContent(
    shown: ImmutableSet<CoatOption?>,
    modifier: Modifier = Modifier,
    onCoatToggle: (CoatOption?) -> Unit = {},
    onClear: () -> Unit = {},
) {
    Column(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.map_coats_title), style = MaterialTheme.typography.titleMedium)
        CoatGrid(selected = shown, onCoatClick = onCoatToggle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = null in shown,
                onClick = { onCoatToggle(null) },
                label = { Text(stringResource(R.string.coat_not_specified)) },
            )
            TextButton(onClick = onClear, enabled = shown.isNotEmpty()) {
                Text(stringResource(R.string.map_coats_all))
            }
        }
    }
}

@ThemePreviews
@Composable
private fun MapCoatContentPreview() {
    CatsRadarTheme { MapCoatContent(shown = persistentSetOf(CoatOption.GINGER, CoatOption.BLACK, null)) }
}
