package dev.catsradar.ui.counter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.coat.coatShapeFor
import dev.catsradar.ui.coat.labelRes
import kotlinx.collections.immutable.ImmutableList

const val CoatTrayFaceTestTag = "coat-tray-face"
const val CoatTrayTestTag = "coat-tray"

private val TrayFaceSize = 40.dp
private val BadgeOverhang = 4.dp
private val TrayCatSize = TrayFaceSize + BadgeOverhang
private val TrayGap = 10.dp

// Rows stack from the bottom, so a cat that opens a row opens it above the others and no counted cat moves.
@Composable
internal fun CountTray(
    tray: ImmutableList<CoatOption?>,
    modifier: Modifier = Modifier,
    onCatClick: (TrayCatInteraction) -> Unit = {},
) {
    Layout(
        content = {
            tray.forEachIndexed { index, coat ->
                TrayCat(
                    coat = coat,
                    modifier = Modifier.semantics { traversalIndex = index.toFloat() },
                    onClick = { onCatClick(TrayCatInteraction(index, coat)) },
                )
            }
        },
        modifier = modifier.fillMaxWidth().testTag(CoatTrayTestTag).semantics { isTraversalGroup = true },
    ) { measurables, constraints ->
        val cell = TrayCatSize.roundToPx()
        val gap = TrayGap.roundToPx()
        val cats = measurables.map { it.measure(Constraints.fixed(cell, cell)) }
        val perRow = ((constraints.maxWidth + gap) / (cell + gap)).coerceAtLeast(1)
        val rows = (cats.size + perRow - 1) / perRow
        val height = (rows * (cell + gap) - gap).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            cats.forEachIndexed { index, cat ->
                val rowFromBottom = index / perRow
                cat.place(x = index % perRow * (cell + gap), y = height - cell - rowFromBottom * (cell + gap))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrayCat(coat: CoatOption?, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val label = stringResource(coat?.labelRes() ?: R.string.coat_none)
    val remove = stringResource(R.string.counter_coat_count_remove)
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    // The × sits inside the touch target, which reaches past the face to take it in.
    Box(
        modifier = modifier
            .size(TrayCatSize)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = remove,
                onClick = onClick
            )
            .semantics { contentDescription = label },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(TrayFaceSize)
                .testTag(CoatTrayFaceTestTag)
                .clip(coatShapeFor(coat).toShape())
                .background(colors.surfaceContainerHighest)
                .indication(interactionSource, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            if (coat != null) {
                CatFace(coat = coat, modifier = Modifier.size(30.dp))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_pets),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = null,
            tint = colors.surface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(16.dp)
                .background(colors.onSurface, CircleShape)
                .padding(3.dp),
        )
    }
}
