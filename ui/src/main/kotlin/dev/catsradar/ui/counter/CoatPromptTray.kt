package dev.catsradar.ui.counter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CatFace
import dev.catsradar.ui.coat.coatShapeFor
import dev.catsradar.ui.coat.labelRes

const val CoatTrayFaceTestTag = "coat-tray-face"
const val CoatTrayTestTag = "coat-tray"

private val TrayFaceSize = 40.dp
private val BadgeOverhang = 4.dp
private val TrayCatSize = TrayFaceSize + BadgeOverhang

// The room of a full tray from the first moment, so a count never moves a counted cat or anything below the tray.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CountTray(
    counting: CoatCountState,
    modifier: Modifier = Modifier,
    onCatClick: (TrayCatInteraction) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxWidth().testTag(CoatTrayTestTag), contentAlignment = Alignment.CenterStart) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(CoatCountState.MOST_CATS) { index ->
                if (index < counting.tray.size) {
                    val coat = counting.tray[index]
                    TrayCat(coat = coat, onClick = { onCatClick(TrayCatInteraction(index, coat)) })
                } else {
                    Spacer(Modifier.size(TrayCatSize))
                }
            }
        }
        if (counting.tray.isEmpty()) {
            Text(
                text = stringResource(R.string.counter_coat_count_tray_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
