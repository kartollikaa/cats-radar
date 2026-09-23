package dev.catsradar.ui.coat

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

private val FaceSize = 34.dp
private val CellWidth = 72.dp
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

/** The same grid with any number of coats marked, for choosing several at once; a null marks none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoatGrid(
    selected: ImmutableSet<CoatOption?>,
    modifier: Modifier = Modifier,
    onCoatClick: (CoatOption) -> Unit = {},
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = CoatsPerRow,
    ) {
        CoatOption.entries.forEach { coat ->
            CoatColumn(
                coat = coat,
                selected = coat in selected,
                modifier = Modifier.fillMaxRowHeight(),
                onClick = { onCoatClick(coat) },
            )
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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstShownCoat(selected))
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = CoatOption.entries, key = { it.name }) { coat ->
            CoatColumn(
                coat = coat,
                selected = coat == selected,
                onClick = { onCoatClick(coat.takeIf { it != selected }) },
            )
        }
    }
}

/** The row opens one coat before the selected one, so it visibly scrolls both ways. */
private fun firstShownCoat(selected: CoatOption?): Int = ((selected?.ordinal ?: 0) - 1).coerceAtLeast(0)

@Composable
private fun CoatColumn(
    coat: CoatOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
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
        CatFace(coat = coat, modifier = Modifier.size(FaceSize))
        Text(
            text = stringResource(coat.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            // Most names wrap to two lines; the short ones keep that height so every ring is the same size.
            minLines = 2,
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
private fun CoatPickerPreview() {
    CatsRadarTheme {
        Surface { CoatPicker(selected = CoatOption.GREY_WHITE) }
    }
}
