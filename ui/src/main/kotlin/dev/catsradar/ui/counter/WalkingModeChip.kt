package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/**
 * Starts and stops the walking notification. It lives on the Counter because that is the screen
 * someone is on when they set out; Settings is a place you visit once.
 */
@Composable
internal fun WalkingModeChip(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    FilterChip(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        label = {
            Text(
                text = stringResource(
                    if (checked) R.string.counter_walking_on else R.string.counter_walking_off,
                ),
            )
        },
        modifier = modifier,
    )
}

@ThemePreviews
@Composable
private fun WalkingModeChipPreview() {
    CatsRadarTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            WalkingModeChip(checked = false)
            WalkingModeChip(checked = true)
        }
    }
}
