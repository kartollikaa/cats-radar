package dev.catsradar.ui.counter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

private val RollSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

@Immutable
private data class ShownCount(val label: String, val count: Int?)

/** The count, as a button: squashes under a press, and rolls up on a tally and down on an undo. */
@Composable
internal fun TallyBlock(
    totalLabel: String,
    count: Int?,
    tapBurst: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tallyPress",
    )
    val tallyLabel = stringResource(R.string.counter_tally)
    val shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp, bottomEnd = 48.dp, bottomStart = 16.dp)
    // Clickable outside the scale: squashing the block must not shrink what a held press can land on.
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = tallyLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = totalLabel.ifEmpty { tallyLabel } },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .indication(interactionSource, ripple()),
            shape = shape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                // Mid-roll the old and the new number are both drawn; the block's own label is the total.
                RollingCount(
                    shown = ShownCount(totalLabel, count),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).clearAndSetSemantics {},
                )
                TapBurst(
                    count = tapBurst,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun RollingCount(shown: ShownCount, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = shown,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            val from = initialState.count
            val to = targetState.count
            if (from == null || to == null) {
                (EnterTransition.None togetherWith ExitTransition.None).using(null)
            } else {
                val rising = to >= from
                val enter = slideInVertically(RollSpring) { height -> if (rising) height else -height } + fadeIn()
                val exit = slideOutVertically(RollSpring) { height -> if (rising) -height else height } + fadeOut()
                (enter togetherWith exit).using(SizeTransform(clip = false))
            }
        },
        label = "count",
    ) { target ->
        Text(
            text = target.label,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 112.sp),
            style = MaterialTheme.typography.displayLarge.copy(lineHeight = 1.em),
        )
    }
}

// A badge rather than bare text: a wide number in a short block reaches this corner, and the
// burst has to stay legible over it. TalkBack reads the total from the block instead.
@Composable
private fun TapBurst(count: Int?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = count != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = modifier.clearAndSetSemantics {},
    ) {
        // Held after the state clears so the exit animation has something to fade out.
        val lastShown = remember { mutableIntStateOf(1) }
        count?.let { lastShown.intValue = it }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = stringResource(R.string.counter_tap_burst, lastShown.intValue),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun TallyBlockPreview() {
    CatsRadarTheme {
        TallyBlock(
            totalLabel = "42",
            count = 42,
            tapBurst = 3,
            modifier = Modifier.fillMaxWidth().height(320.dp),
        )
    }
}

@ThemePreviews
@Composable
private fun TallyBlockCrampedPreview() {
    CatsRadarTheme {
        TallyBlock(
            totalLabel = "10000",
            count = 10000,
            tapBurst = 12,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
    }
}
