package dev.catsradar.ui.counter

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import dev.catsradar.ui.components.SheetHeader
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
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
    // Anchored at its bottom, as the sheet is, so what changes size grows upward, above what one taps next.
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState(), reverseScrolling = true)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The tray brings its own gap, which opens and closes with it.
        Column {
            SheetHeader(
                title = promptTitle(counting),
                supporting = promptHint(counting),
                titleStyle = MaterialTheme.typography.headlineSmallEmphasized,
                leading = { PromptLead(prompt.thumbPath) },
            )
            val tray = counting?.tray ?: persistentListOf()
            val shownTray = rememberLastCounted(tray)
            AnimatedVisibility(
                visible = tray.isNotEmpty(),
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
            ) {
                CountTray(
                    tray = shownTray,
                    onCatClick = onTrayCatClick,
                    modifier = Modifier.padding(top = 16.dp).animateContentSize(alignment = Alignment.BottomStart),
                )
            }
        }
        OneOrSeveral(several = counting != null, onOneCatClick = onOneCatClick, onSeveralClick = onSeveralClick)
        if (counting == null) {
            CoatGrid(selected = persistentSetOf(), onCoatClick = onCoatClick, keepsUnspecifiedPlace = true)
        } else {
            CoatGrid(
                selected = counting.counts.keys.toImmutableSet(),
                counts = counting.counts,
                enabled = counting.canAdd,
                onCoatClick = onCoatClick,
                onUnspecifiedClick = onUnseenClick,
                unspecifiedLabel = R.string.coat_none,
            )
        }
        // Not now at the start, so Save coming and going never moves it.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSkipClick) { Text(stringResource(R.string.counter_coat_prompt_skip)) }
            val count = counting?.catCount
            if (count != null) {
                SaveButton(count = count, onClick = onSaveClick)
            } else {
                Box(modifier = Modifier.onlyItsHeight().clearAndSetSemantics {}) { SaveButton(count = 1) }
            }
        }
    }
}

// The cats a tray last held, so they stay in it while it closes.
@Composable
private fun rememberLastCounted(tray: ImmutableList<CoatOption?>): ImmutableList<CoatOption?> {
    val last = remember { mutableStateOf(tray) }
    SideEffect { if (tray.isNotEmpty()) last.value = tray }
    return tray.ifEmpty { last.value }
}

@Composable
private fun SaveButton(count: Int, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Button(onClick = onClick, modifier = modifier) {
        Text(pluralStringResource(R.plurals.counter_coat_count_save, count, count))
    }
}

// Save's height with nothing drawn: the row keeps it before the first cat is counted and while asking.
private fun Modifier.onlyItsHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, placeable.height) {}
}

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
    val scheme = MaterialTheme.colorScheme
    ToggleButton(
        checked = checked,
        onCheckedChange = { if (!checked) onClick() },
        shapes = shapes,
        // Tonal when chosen, so Save stays the sheet's one filled button.
        colors = ToggleButtonDefaults.colors(
            containerColor = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
            checkedContainerColor = scheme.secondaryContainer,
            checkedContentColor = scheme.onSecondaryContainer,
        ),
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
