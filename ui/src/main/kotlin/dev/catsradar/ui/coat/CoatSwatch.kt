package dev.catsradar.ui.coat

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

private val SwatchSize = 40.dp
private val FaceSize = 34.dp
private val FaceOutline = 1.dp
private val SwatchColumnWidth = 68.dp
private const val CoatsPerRow = 4

/**
 * The coats a person can pick from, as a grid. Several of them are near-identical as colours — grey,
 * grey and white, black — so every swatch carries its name as well: colour alone would not tell
 * them apart, which the design brief called out explicitly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoatGrid(
    modifier: Modifier = Modifier,
    highlighted: CoatOption? = null,
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
                selected = coat == highlighted,
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
    onCoatClick: (CoatOption?) -> Unit = {},
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
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

@Composable
private fun CoatColumn(
    coat: CoatOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .width(SwatchColumnWidth)
            .selectable(selected = selected, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Swatch(coat = coat, selected = selected)
        Text(
            text = stringResource(coat.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Swatch(coat: CoatOption, selected: Boolean, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val base = coat.baseColor()
    val hasWhite = coat.hasWhite()
    val selectionBackground = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Box(
        modifier = modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(selectionBackground),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(FaceSize)) {
            val face = catFacePath(size)
            clipPath(face) {
                drawRect(color = base)
                if (hasWhite) {
                    // A white half, not a lighter shade: "grey" and "grey and white" have to be
                    // distinguishable at a glance, and two similar fills are not.
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(size.width / 2f, 0f),
                        size = Size(size.width / 2f, size.height),
                    )
                }
            }
            // Without it a white cat on a light surface is an invisible cat.
            drawPath(face, color = outline, style = Stroke(width = FaceOutline.toPx()))
        }
    }
}

/**
 * A cat's head in [size]: an oval face with a triangular ear rising from each top corner, unioned
 * into one contour so stroking the outline does not also draw where the ears cross the face.
 */
private fun catFacePath(size: Size): Path {
    fun x(fraction: Float) = size.width * fraction
    fun y(fraction: Float) = size.height * fraction

    val head = Path().apply {
        addOval(Rect(left = x(0.05f), top = y(0.24f), right = x(0.95f), bottom = y(0.99f)))
    }
    val ears = Path().apply {
        moveTo(x(0.09f), y(0.01f))
        lineTo(x(0.44f), y(0.30f))
        lineTo(x(0.13f), y(0.56f))
        close()

        moveTo(x(0.91f), y(0.01f))
        lineTo(x(0.56f), y(0.30f))
        lineTo(x(0.87f), y(0.56f))
        close()
    }
    return Path().apply { op(head, ears, PathOperation.Union) }
}

private fun CoatOption.hasWhite(): Boolean = when (this) {
    CoatOption.GINGER_WHITE,
    CoatOption.BROWN_WHITE,
    CoatOption.GREY_WHITE,
    CoatOption.BLACK_WHITE,
    CoatOption.TRICOLOR_MOSTLY_WHITE,
    CoatOption.TRICOLOR_LITTLE_WHITE,
    -> true
    else -> false
}

private fun CoatOption.baseColor(): Color = when (this) {
    CoatOption.GINGER, CoatOption.GINGER_WHITE -> Color(0xFFE8833A)
    CoatOption.WHITE -> Color(0xFFF5F5F5)
    CoatOption.TRICOLOR_MOSTLY_WHITE, CoatOption.TRICOLOR_LITTLE_WHITE -> Color(0xFF8D6E4A)
    CoatOption.BROWN, CoatOption.BROWN_WHITE -> Color(0xFF6D4C2F)
    CoatOption.GREY, CoatOption.GREY_WHITE -> Color(0xFF9E9E9E)
    CoatOption.BLACK, CoatOption.BLACK_WHITE -> Color(0xFF2B2B2B)
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
