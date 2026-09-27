package dev.catsradar.ui.counter

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.presentation.counter.CoatPromptState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatGrid
import dev.catsradar.ui.coat.coatShapeFor
import dev.catsradar.ui.components.CatsRadarBottomSheet
import dev.catsradar.ui.components.SheetActions
import dev.catsradar.ui.components.SheetHeader
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableSet

const val CoatPromptPawTestTag = "coat-prompt-paw"

/** The tray cat at [index], of [coat]. */
data class TrayCatInteraction(val index: Int, val coat: CoatOption?)

/** How the coat question after a photo was answered. */
sealed interface CoatPromptAction {
    data class CoatPicked(val coat: CoatOption) : CoatPromptAction

    data object UnseenPicked : CoatPromptAction

    data class TrayCatClicked(val tap: TrayCatInteraction) : CoatPromptAction

    data object OneCatClicked : CoatPromptAction

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
            onOneCatClick = { onAction(CoatPromptAction.OneCatClicked) },
            onSeveralClick = { onAction(CoatPromptAction.SeveralClicked) },
            onSaveClick = { onAction(CoatPromptAction.SaveClicked) },
            onSkipClick = { onAction(CoatPromptAction.Dismissed) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoatPrompt(
    prompt: CoatPromptState,
    modifier: Modifier = Modifier,
    onCoatClick: (CoatOption) -> Unit = {},
    onUnseenClick: () -> Unit = {},
    onTrayCatClick: (TrayCatInteraction) -> Unit = {},
    onOneCatClick: () -> Unit = {},
    onSeveralClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onSkipClick: () -> Unit = {},
) {
    val counting = prompt.counting
    Column(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(
            title = promptTitle(counting),
            supporting = promptHint(counting),
            titleStyle = MaterialTheme.typography.headlineSmallEmphasized,
            leading = { PromptLead(prompt.thumbPath) },
        )
        OneOrSeveral(several = counting != null, onOneCatClick = onOneCatClick, onSeveralClick = onSeveralClick)
        if (counting == null) {
            CoatGrid(onCoatClick = onCoatClick)
        } else {
            CountTray(counting = counting, onCatClick = onTrayCatClick)
            CoatGrid(
                selected = counting.counts.keys.toImmutableSet(),
                counts = counting.counts,
                enabled = counting.canAdd,
                onCoatClick = onCoatClick,
                onUnspecifiedClick = onUnseenClick,
                unspecifiedLabel = R.string.coat_none,
            )
        }
        SheetActions {
            TextButton(onClick = onSkipClick) { Text(stringResource(R.string.counter_coat_prompt_skip)) }
            counting?.catCount?.let { count ->
                Button(onClick = onSaveClick) {
                    Text(pluralStringResource(R.plurals.counter_coat_count_save, count, count))
                }
            }
        }
    }
}

@Composable
private fun promptTitle(counting: CoatCountState?): String {
    val count = counting?.catCount
    return when {
        counting == null -> stringResource(R.string.counter_coat_prompt_title)
        count == null -> stringResource(R.string.counter_coat_count_title_empty)
        else -> pluralStringResource(R.plurals.counter_coat_count_title, count, count)
    }
}

@Composable
private fun promptHint(counting: CoatCountState?): String = stringResource(
    when {
        counting == null -> R.string.counter_coat_prompt_hint
        counting.canAdd -> R.string.counter_coat_count_hint
        else -> R.string.counter_coat_count_full
    },
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PromptLead(thumbPath: String?) {
    if (thumbPath != null) {
        AsyncImage(
            model = thumbPath,
            contentDescription = stringResource(R.string.counter_coat_prompt_photo),
            modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(64.dp)
                .testTag(CoatPromptPawTestTag)
                .clip(coatShapeFor(null).toShape())
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_pets),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OneOrSeveral(
    several: Boolean,
    modifier: Modifier = Modifier,
    onOneCatClick: () -> Unit = {},
    onSeveralClick: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ModeButton(
            labelRes = R.string.counter_coat_prompt_one,
            checked = !several,
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier = Modifier.weight(1f),
            onClick = onOneCatClick,
        )
        ModeButton(
            labelRes = R.string.counter_coat_prompt_several,
            checked = several,
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier.weight(1f),
            onClick = onSeveralClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeButton(
    @StringRes labelRes: Int,
    checked: Boolean,
    shapes: ToggleButtonShapes,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { if (!checked) onClick() },
        shapes = shapes,
        modifier = modifier.semantics { role = Role.RadioButton },
    ) {
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
            )
        }
        Text(text = stringResource(labelRes), maxLines = 1)
    }
}

@ThemePreviews
@Composable
private fun CoatPromptPreview() {
    CatsRadarTheme { CoatPrompt(prompt = CoatPromptState(catId = "cat", photoId = "cat", thumbPath = null)) }
}

@ThemePreviews
@Composable
private fun CoatPromptCountingPreview() {
    CatsRadarTheme { CoatPrompt(prompt = sampleCounting) }
}

@ThemePreviews
@Composable
private fun CoatPromptCountingEmptyPreview() {
    CatsRadarTheme { CoatPrompt(prompt = CoatPromptState("cat", "cat", thumbPath = null, counting = CoatCountState())) }
}

private val sampleCounting = CoatPromptState(
    catId = "cat",
    photoId = "cat",
    thumbPath = null,
    counting = CoatCountState(
        tray = persistentListOf(CoatOption.GINGER, CoatOption.BLACK_WHITE, null, CoatOption.GINGER),
    ),
)
