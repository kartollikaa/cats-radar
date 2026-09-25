package dev.catsradar.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dev.catsradar.ui.components.SheetHeader
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoatPromptSheet(
    prompt: CoatPromptState,
    modifier: Modifier = Modifier,
    onCoatClick: (CoatOption) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        CoatPrompt(prompt = prompt, onCoatClick = onCoatClick, onSkipClick = onDismiss)
    }
}

@Composable
fun CoatPrompt(
    prompt: CoatPromptState,
    modifier: Modifier = Modifier,
    onCoatClick: (CoatOption) -> Unit = {},
    onSkipClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(
            title = stringResource(R.string.counter_coat_prompt_title),
            supporting = stringResource(R.string.counter_coat_prompt_hint),
            leading = prompt.thumbPath?.let { path ->
                {
                    AsyncImage(
                        model = path,
                        contentDescription = stringResource(R.string.counter_coat_prompt_photo),
                        modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop,
                    )
                }
            },
        )
        CoatGrid(onCoatClick = onCoatClick)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSkipClick) { Text(stringResource(R.string.counter_coat_prompt_skip)) }
        }
    }
}

@ThemePreviews
@Composable
private fun CoatPromptPreview() {
    CatsRadarTheme { CoatPrompt(prompt = CoatPromptState(thumbPath = null)) }
}
