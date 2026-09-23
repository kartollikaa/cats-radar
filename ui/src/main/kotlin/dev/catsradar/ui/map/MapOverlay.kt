package dev.catsradar.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.map.MapState
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/** What sits over the map: the focused outing, the heat and coat controls, and a filter that matched no cat. */
@Composable
internal fun MapOverlay(
    state: MapState.Located,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onFocusClear: () -> Unit = {},
    onHeatToggle: () -> Unit = {},
    onCoatsClick: () -> Unit = {},
) {
    Box(modifier = modifier.padding(contentPadding)) {
        if (state.filterMatchesNone) NoCatOfTheseCoats(modifier = Modifier.align(Alignment.Center))
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            state.focus?.let { focused -> OutingFocusChip(label = focused.label, onClose = onFocusClear) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapChip(label = stringResource(R.string.map_heat), selected = state.heat, onClick = onHeatToggle)
                MapChip(
                    label = stringResource(R.string.map_coats),
                    selected = state.coatFilterActive,
                    onClick = onCoatsClick,
                )
            }
        }
    }
}

@Composable
private fun MapChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = FilterChipDefaults.filterChipElevation(elevation = 3.dp),
    )
}

@Composable
private fun NoCatOfTheseCoats(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(32.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = stringResource(R.string.map_filter_none),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun OutingFocusChip(label: String, modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.map_focus_label, label), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.map_focus_close),
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun OutingFocusChipPreview() {
    CatsRadarTheme { OutingFocusChip(label = "Today, 14:10", modifier = Modifier.padding(16.dp)) }
}

@ThemePreviews
@Composable
private fun MapChipPreview() {
    CatsRadarTheme {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapChip(label = "Heatmap", selected = true)
            MapChip(label = "Coats", selected = false)
        }
    }
}
