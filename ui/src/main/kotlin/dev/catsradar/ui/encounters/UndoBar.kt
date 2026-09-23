package dev.catsradar.ui.encounters

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun UndoBar(removedCount: Int, modifier: Modifier = Modifier, onUndoClick: () -> Unit = {}) {
    Snackbar(
        modifier = modifier,
        action = {
            TextButton(
                onClick = onUndoClick,
                colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
            ) {
                Text(text = stringResource(R.string.encounters_undo))
            }
        },
    ) {
        Text(text = pluralStringResource(R.plurals.encounters_removed, removedCount, removedCount))
    }
}

@ThemePreviews
@Composable
private fun UndoBarPreview() {
    CatsRadarTheme { UndoBar(removedCount = 3, modifier = Modifier.padding(16.dp)) }
}
