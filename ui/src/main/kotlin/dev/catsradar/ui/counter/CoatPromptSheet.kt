package dev.catsradar.ui.counter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.presentation.counter.CoatPromptState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.coat.CoatGrid
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.components.CatsRadarBottomSheet
import dev.catsradar.ui.components.SheetActions
import dev.catsradar.ui.components.SheetHeader
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableSet

private val TrayFaceSize = 40.dp

/** How the coat question after a photo was answered. */
sealed interface CoatPromptAction {
    data class CoatPicked(val coat: CoatOption) : CoatPromptAction

    data object UnseenPicked : CoatPromptAction

    data class TrayCatClicked(val index: Int) : CoatPromptAction

    data object SeveralClicked : CoatPromptAction

    data object SaveClicked : CoatPromptAction

    data object Dismissed : CoatPromptAction
}

@Composable
internal fun CoatPromptSheet(
    prompt: CoatPromptState,
    modifier: Modifier = Modifier,
    onAction: (CoatPromptAction) -> Unit = {},
) {
    CatsRadarBottomSheet(onDismissRequest = { onAction(CoatPromptAction.Dismissed) }, modifier = modifier) {
        CoatPrompt(
            prompt = prompt,
            onCoatClick = { onAction(CoatPromptAction.CoatPicked(it)) },
            onUnseenClick = { onAction(CoatPromptAction.UnseenPicked) },
            onTrayCatClick = { onAction(CoatPromptAction.TrayCatClicked(it)) },
            onSeveralClick = { onAction(CoatPromptAction.SeveralClicked) },
            onSaveClick = { onAction(CoatPromptAction.SaveClicked) },
            onSkipClick = { onAction(CoatPromptAction.Dismissed) },
        )
    }
}

@Composable
fun CoatPrompt(
    prompt: CoatPromptState,
    modifier: Modifier = Modifier,
    onCoatClick: (CoatOption) -> Unit = {},
    onUnseenClick: () -> Unit = {},
    onTrayCatClick: (Int) -> Unit = {},
    onSeveralClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onSkipClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val counting = prompt.counting
        if (counting == null) {
            SheetHeader(
                title = stringResource(R.string.counter_coat_prompt_title),
                supporting = stringResource(R.string.counter_coat_prompt_hint),
                leading = prompt.thumbPath?.let { path -> { PromptPhoto(path, Modifier.size(64.dp)) } },
            )
            CoatGrid(onCoatClick = onCoatClick)
            SheetActions {
                TextButton(onClick = onSeveralClick) { Text(stringResource(R.string.counter_coat_prompt_several)) }
                TextButton(onClick = onSkipClick) { Text(stringResource(R.string.counter_coat_prompt_skip)) }
            }
        } else {
            SheetHeader(
                title = counting.catCount?.let { pluralStringResource(R.plurals.counter_coat_count_title, it, it) }
                    ?: stringResource(R.string.counter_coat_count_title_empty),
                supporting = stringResource(R.string.counter_coat_count_hint),
            )
            CountTray(thumbPath = prompt.thumbPath, counting = counting, onCatClick = onTrayCatClick)
            CoatGrid(
                selected = counting.counts.keys.toImmutableSet(),
                counts = counting.counts,
                enabled = counting.canAdd,
                onCoatClick = onCoatClick,
                onUnspecifiedClick = onUnseenClick,
            )
            SheetActions {
                TextButton(onClick = onSkipClick) { Text(stringResource(R.string.counter_coat_prompt_skip)) }
                counting.catCount?.let { count ->
                    Button(onClick = onSaveClick) {
                        Text(pluralStringResource(R.plurals.counter_coat_count_save, count, count))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountTray(
    thumbPath: String?,
    counting: CoatCountState,
    modifier: Modifier = Modifier,
    onCatClick: (Int) -> Unit = {},
) {
    val remove = stringResource(R.string.counter_coat_count_remove)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        thumbPath?.let { PromptPhoto(it, Modifier.size(48.dp)) }
        counting.tray.forEachIndexed { index, coat ->
            val label = stringResource(coat?.labelRes() ?: R.string.coat_not_specified)
            Box(
                modifier = Modifier
                    .size(TrayFaceSize)
                    .clip(CircleShape)
                    .clickable(onClickLabel = remove) { onCatClick(index) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                if (coat != null) {
                    CatFace(coat = coat, modifier = Modifier.size(TrayFaceSize))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_pets),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(TrayFaceSize).padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptPhoto(path: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = path,
        contentDescription = stringResource(R.string.counter_coat_prompt_photo),
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        contentScale = ContentScale.Crop,
    )
}

@ThemePreviews
@Composable
private fun CoatPromptPreview() {
    CatsRadarTheme { CoatPrompt(prompt = CoatPromptState(thumbPath = null)) }
}

@ThemePreviews
@Composable
private fun CoatPromptCountingPreview() {
    CatsRadarTheme { CoatPrompt(prompt = sampleCounting) }
}

@ThemePreviews
@Composable
private fun CoatPromptCountingEmptyPreview() {
    CatsRadarTheme { CoatPrompt(prompt = CoatPromptState(thumbPath = null, counting = CoatCountState())) }
}

private val sampleCounting = CoatPromptState(
    thumbPath = null,
    counting = CoatCountState(
        tray = persistentListOf(CoatOption.GINGER, CoatOption.BLACK_WHITE, null, CoatOption.GINGER),
    ),
)
