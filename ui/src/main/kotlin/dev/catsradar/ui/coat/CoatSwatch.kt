package dev.catsradar.ui.coat

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

private val CellWidth = 70.dp
private val FaceSize = 34.dp
private val PickerGap = 12.dp
private const val CoatsPerRow = 4

/**
 * The coats a person can pick from, as a grid. Several of them are near-identical as colours — grey,
 * grey and white, black — so every swatch carries its name as well: colour alone would not tell
 * them apart, which the design brief called out explicitly.
 */
@Composable
fun CoatGrid(
    modifier: Modifier = Modifier,
    highlighted: CoatOption? = null,
    onCoatClick: (CoatOption) -> Unit = {},
) {
    val selected = remember(highlighted) { persistentSetOf(highlighted) }
    CoatGrid(selected = selected, modifier = modifier, onCoatClick = onCoatClick)
}

/**
 * The same grid with any number of coats marked, for choosing several at once. A null in [selected]
 * marks "no coat", which gets a cell of its own after the coats only when [onUnspecifiedClick] is given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoatGrid(
    selected: ImmutableSet<CoatOption?>,
    modifier: Modifier = Modifier,
    onCoatClick: (CoatOption) -> Unit = {},
    onUnspecifiedClick: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = CoatsPerRow,
    ) {
        CoatOption.entries.forEach { coat ->
            CoatCell(
                label = stringResource(coat.labelRes()),
                selected = coat in selected,
                modifier = Modifier.fillMaxRowHeight(),
                onClick = { onCoatClick(coat) },
            ) {
                CatFace(coat = coat, modifier = Modifier.size(FaceSize))
            }
        }
        onUnspecifiedClick?.let { onClick ->
            CoatCell(
                label = stringResource(R.string.coat_not_specified),
                selected = null in selected,
                modifier = Modifier.fillMaxRowHeight(),
                onClick = onClick,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_pets),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(FaceSize).padding(4.dp),
                )
            }
        }
    }
}

/** A picker that edits one cat's coat: tapping the current coat again clears it. */
@Composable
fun CoatPicker(
    selected: CoatOption?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onCoatClick: (CoatOption?) -> Unit = {},
) {
    val coats = CoatOption.entries
    val coatPitch = with(LocalDensity.current) { CellWidth.roundToPx() + PickerGap.roundToPx() }
    // Opens one coat before the selected one, so the row visibly scrolls both ways.
    val scrollState = rememberScrollState(initial = (coats.indexOf(selected) - 1).coerceAtLeast(0) * coatPitch)
    // Not lazy: every cell is measured, so all of them take the tallest name's height.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(contentPadding)
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(PickerGap),
    ) {
        coats.forEach { coat ->
            CoatCell(
                label = stringResource(coat.labelRes()),
                selected = coat == selected,
                modifier = Modifier.fillMaxHeight(),
                onClick = { onCoatClick(coat.takeIf { it != selected }) },
            ) {
                CatFace(coat = coat, modifier = Modifier.size(FaceSize))
            }
        }
    }
}

@Composable
private fun CoatCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    face: @Composable () -> Unit,
) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier = modifier
            .width(CellWidth)
            // Clipped first so the ripple follows the cell's rounded shape instead of a hard rectangle.
            .clip(MaterialTheme.shapes.small)
            .border(width = 2.dp, color = ring, shape = MaterialTheme.shapes.small)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        face()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@StringRes
fun CoatOption.labelRes(): Int = when (this) {
    CoatOption.GINGER -> R.string.coat_ginger
    CoatOption.GINGER_WHITE -> R.string.coat_ginger_white
    CoatOption.WHITE -> R.string.coat_white
    CoatOption.TRICOLOR_MOSTLY_WHITE -> R.string.coat_tricolor_mostly_white
    CoatOption.TRICOLOR_LITTLE_WHITE -> R.string.coat_tricolor_little_white
    CoatOption.BROWN -> R.string.coat_brown
    CoatOption.BROWN_WHITE -> R.string.coat_brown_white
    CoatOption.GREY -> R.string.coat_grey
    CoatOption.GREY_WHITE -> R.string.coat_grey_white
    CoatOption.BLACK -> R.string.coat_black
    CoatOption.BLACK_WHITE -> R.string.coat_black_white
}

@ThemePreviews
@Composable
private fun CoatGridPreview() {
    CatsRadarTheme {
        Surface { CoatGrid(highlighted = CoatOption.GREY_WHITE) }
    }
}

@ThemePreviews
@Composable
private fun CoatGridWithUnspecifiedPreview() {
    CatsRadarTheme {
        Surface { CoatGrid(selected = persistentSetOf(CoatOption.GINGER, null), onUnspecifiedClick = {}) }
    }
}

@ThemePreviews
@Composable
private fun CoatPickerPreview() {
    CatsRadarTheme {
        Surface { CoatPicker(selected = CoatOption.GREY_WHITE) }
    }
}
