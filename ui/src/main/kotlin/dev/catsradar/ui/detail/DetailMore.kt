package dev.catsradar.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun DetailMore(
    showsOnMap: Boolean,
    modifier: Modifier = Modifier,
    onShowOnMapClick: () -> Unit = {},
    onRemoveClick: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilledTonalIconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_horiz),
                contentDescription = stringResource(R.string.detail_more),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.detail_show_on_map)) },
                onClick = {
                    open = false
                    onShowOnMapClick()
                },
                enabled = showsOnMap,
                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_nav_map), contentDescription = null) },
            )
            val error = MaterialTheme.colorScheme.error
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.detail_remove)) },
                onClick = {
                    open = false
                    onRemoveClick()
                },
                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = null) },
                colors = MenuDefaults.itemColors(textColor = error, leadingIconColor = error),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun DetailMorePreview() {
    CatsRadarTheme { DetailMore(showsOnMap = true) }
}
