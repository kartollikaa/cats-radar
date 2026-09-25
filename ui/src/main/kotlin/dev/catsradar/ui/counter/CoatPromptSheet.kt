package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatPromptState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatGrid
import dev.catsradar.ui.components.CatsRadarBottomSheet
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
internal fun CoatPromptSheet(
    prompt: CoatPromptState,
    modifier: Modifier = Modifier,
    onCoatClick: (CoatOption) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    CatsRadarBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        CoatPromptContent(prompt = prompt, onCoatClick = onCoatClick, onSkipClick = onDismiss)
    }
}

@Composable
private fun CoatPromptContent(
    prompt: CoatPromptState,
    modifier: Modifier = Modifier,
    onCoatClick: (CoatOption) -> Unit = {},
    onSkipClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            prompt.thumbPath?.let {
                AsyncImage(
                    model = it,
                    contentDescription = stringResource(R.string.counter_coat_prompt_photo),
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(
                text = stringResource(R.string.counter_coat_prompt_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        CoatGrid(onCoatClick = onCoatClick)
        TextButton(onClick = onSkipClick, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.counter_coat_prompt_skip))
        }
    }
}

@ThemePreviews
@Composable
private fun CoatPromptContentPreview() {
    CatsRadarTheme { CoatPromptContent(prompt = CoatPromptState(thumbPath = null)) }
}
