package dev.catsradar.ui.counter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

private val TallyShape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp, bottomEnd = 48.dp, bottomStart = 16.dp)
private const val PressedScale = 0.95f
private val PressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)
private val RollSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

@Immutable
private data class ShownCount(val label: String, val count: Int)

/** The count, as a button: squashes under a press, and rolls up on a tally and down on an undo. */
@Composable
internal fun TallyBlock(
    totalLabel: String,
    count: Int,
    tapBurst: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else 1f,
        animationSpec = PressSpring,
        label = "tallyPress",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = TallyShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            RollingCount(shown = ShownCount(totalLabel, count))
            TapBurst(count = tapBurst, modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp))
        }
    }
}

@Composable
private fun RollingCount(shown: ShownCount, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = shown,
        modifier = modifier,
        transitionSpec = {
            val rising = targetState.count >= initialState.count
            val enter = slideInVertically(RollSpring) { height -> if (rising) height else -height } + fadeIn()
            val exit = slideOutVertically(RollSpring) { height -> if (rising) -height else height } + fadeOut()
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "count",
    ) { target ->
        Text(
            text = target.label,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 112.sp, lineHeight = 112.sp),
        )
    }
}

@Composable
private fun TapBurst(count: Int?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = count != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Held after the state clears so the exit animation has something to fade out.
        val lastShown = remember { mutableIntStateOf(1) }
        count?.let { lastShown.intValue = it }
        Text(
            text = stringResource(R.string.counter_tap_burst, lastShown.intValue),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@ThemePreviews
@Composable
private fun TallyBlockPreview() {
    CatsRadarTheme {
        TallyBlock(totalLabel = "42", count = 42, tapBurst = 3, modifier = Modifier.fillMaxWidth().height(320.dp))
    }
}
