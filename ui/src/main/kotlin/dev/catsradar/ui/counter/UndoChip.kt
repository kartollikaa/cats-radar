package dev.catsradar.ui.counter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun UndoChip(visible: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        AssistChip(onClick = onClick, label = { Text(text = stringResource(R.string.counter_undo), maxLines = 1) })
    }
}

@ThemePreviews
@Composable
private fun UndoChipPreview() {
    CatsRadarTheme { UndoChip(visible = true, modifier = Modifier.padding(16.dp)) }
}
