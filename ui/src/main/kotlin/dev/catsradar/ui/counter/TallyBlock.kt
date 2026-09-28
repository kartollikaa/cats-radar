package dev.catsradar.ui.counter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.counter.CounterMilestoneState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.counter.MilestoneMomentState
import dev.catsradar.presentation.statistics.MilestoneState
import dev.catsradar.ui.R
import dev.catsradar.ui.statistics.label
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val MilestoneArcTestTag = "milestone-arc"
const val CountNumberTestTag = "count-number"
const val TapBurstTestTag = "tap-burst"

/** The count, as a button: squashes under a press, and rolls up on a tally and down on an undo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TallyBlock(
    totalLabel: String,
    count: Int?,
    tapBurst: Int?,
    modifier: Modifier = Modifier,
    milestone: CounterMilestoneState? = null,
    moment: MilestoneMomentState? = null,
    currentOuting: CurrentOutingState? = null,
    walking: Boolean = false,
    undoVisible: Boolean = false,
    onClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, animationSpec = press, label = "tallyPress")
    val turn by rememberCookieTurn(count)
    val breath by rememberCookieBreath(breathing = walking)
    val rungBounce by rememberRungBounce(moment)
    val cookieColors = rememberCookieColors(walking)
    val tallyLabel = stringResource(R.string.counter_tally)
    val cookie = MaterialShapes.Cookie12Sided.toShape()
    val cookieInBlock = remember(cookie) { CentredSquare(cookie) }
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
        contentAlignment = Alignment.Center,
    ) {
        // The block's own size, so the ripple starts where the finger is.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale * breath * rungBounce
                    scaleY = scale * breath * rungBounce
                    rotationZ = turn
                }
                .clip(cookieInBlock)
                .background(cookieColors.fill)
                .indication(interactionSource, ripple()),
        )
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = scale * rungBounce
                    scaleY = scale * rungBounce
                },
            contentAlignment = Alignment.Center,
        ) {
            CookieContent(totalLabel, count, milestone, moment, currentOuting, cookieColors)
        }
        TagsAndUndo(
            milestone = milestone,
            moment = moment,
            currentOuting = currentOuting,
            tapBurst = tapBurst,
            scale = { scale * rungBounce },
            undoVisible = undoVisible,
            onUndoClick = onUndoClick,
        )
    }
}

/** The ring's tags, with the run's "+N" and Undo at the block's end, level with the cookie's top and bottom. */
@Composable
private fun BoxScope.TagsAndUndo(
    milestone: CounterMilestoneState?,
    moment: MilestoneMomentState?,
    currentOuting: CurrentOutingState?,
    tapBurst: Int?,
    scale: () -> Float,
    undoVisible: Boolean,
    onUndoClick: () -> Unit,
) {
    var undoWidth by remember { mutableIntStateOf(0) }
    RingTags(
        milestone = milestone,
        moment = moment,
        currentOuting = currentOuting,
        scale = scale,
        undoWidth = { if (undoVisible) undoWidth else 0 },
    )
    Box(modifier = Modifier.matchParentSize()) {
        TapBurst(count = tapBurst, modifier = Modifier.atCookieEnd(bottom = false))
        UndoButton(
            visible = undoVisible,
            onClick = onUndoClick,
            modifier = Modifier.atCookieEnd(bottom = true).onSizeChanged { undoWidth = it.width },
        )
    }
}

// Beside a cookie narrower than its block, clear of the ring's tags; in its corner when it fills the width.
private fun Modifier.atCookieEnd(bottom: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val square = min(constraints.maxWidth, constraints.maxHeight)
    val top = (constraints.maxHeight - square) / 2
    val y = if (bottom) top + square - placeable.height else top
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.placeRelative(constraints.maxWidth - placeable.width, y)
    }
}

@Immutable
private data class CookieColors(val fill: Color, val ink: Color)

/** The cookie in `primaryContainer`, or in `tertiaryContainer` while a walk is on, easing between the two. */
@Composable
private fun rememberCookieColors(walking: Boolean): CookieColors {
    val colors = MaterialTheme.colorScheme
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val fill by animateColorAsState(
        targetValue = if (walking) colors.tertiaryContainer else colors.primaryContainer,
        animationSpec = spec,
        label = "cookieFill",
    )
    val ink by animateColorAsState(
        targetValue = if (walking) colors.onTertiaryContainer else colors.onPrimaryContainer,
        animationSpec = spec,
        label = "cookieInk",
    )
    return CookieColors(fill = fill, ink = ink)
}

@Composable
private fun BoxScope.CookieContent(
    totalLabel: String,
    count: Int?,
    milestone: CounterMilestoneState?,
    moment: MilestoneMomentState?,
    currentOuting: CurrentOutingState?,
    colors: CookieColors,
) {
    if (moment != null) {
        RungRing(moment = moment, modifier = Modifier.fillMaxSize(RingFraction))
    } else {
        milestone?.let {
            MilestoneArc(fraction = it.fraction, colors = colors, modifier = Modifier.fillMaxSize(RingFraction))
        }
    }
    CompositionLocalProvider(LocalContentColor provides colors.ink) {
        CountAndCaption(
            totalLabel = totalLabel,
            count = count,
            modifier = Modifier.clearOfRingTags(
                top = rememberGoalTagHeight(milestone),
                bottom = rememberOutingTagHeight(currentOuting),
            ),
        )
    }
}

@Composable
private fun MilestoneArc(fraction: Float, colors: CookieColors, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = fraction,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "milestoneArc",
    )
    val track = colors.ink.copy(alpha = 0.15f)
    val arc = MaterialTheme.colorScheme.primary
    val rim = colors.fill
    Canvas(modifier = modifier.testTag(MilestoneArcTestTag)) {
        val stroke = size.minDimension * RingStrokeFraction
        val topLeft = Offset(stroke / 2, stroke / 2)
        val ring = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = ring,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = arc,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = ring,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (progress > 0f) {
            val angle = (360f * progress - 90f) * PI / 180
            val head = Offset(
                x = size.width / 2 + ring.width / 2 * cos(angle).toFloat(),
                y = size.height / 2 + ring.height / 2 * sin(angle).toFloat(),
            )
            drawCircle(color = rim, radius = stroke * 1.7f, center = head)
            drawCircle(color = arc, radius = stroke * 1.15f, center = head)
        }
    }
}

// The caption gives way first: it is dropped when keeping it would push the number below its smallest size.
@Composable
private fun CountAndCaption(totalLabel: String, count: Int?, modifier: Modifier = Modifier) {
    Layout(
        contents = listOf(
            {
                // Mid-roll the old and the new number are both drawn; the block's own label is the total.
                RollingCount(
                    label = totalLabel,
                    count = count,
                    modifier = Modifier.testTag(CountNumberTestTag).clearAndSetSemantics {},
                )
            },
            {
                if (count != null) {
                    Text(
                        text = pluralStringResource(R.plurals.counter_count_caption, count),
                        style = MaterialTheme.typography.titleMedium,
                        // Its own node, so its words stay out of the block's label.
                        modifier = Modifier.semantics(mergeDescendants = true) { hideFromAccessibility() },
                    )
                }
            },
        ),
        modifier = modifier,
    ) { (numberItems, captionItems), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val caption = captionItems.firstOrNull()?.measure(loose)
            ?.takeIf { it.height + MinCountSize.roundToPx() <= constraints.maxHeight }
        val numberRoom = constraints.maxHeight - (caption?.height ?: 0)
        val number = numberItems.single().measure(loose.copy(maxHeight = numberRoom))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val top = (constraints.maxHeight - number.height - (caption?.height ?: 0)) / 2
            number.place((constraints.maxWidth - number.width) / 2, top)
            caption?.place((constraints.maxWidth - caption.width) / 2, top + number.height)
        }
    }
}

/** [shape] in the largest square that fits, centred, where the block's `aspectRatio(1f)` child sits. */
internal class CentredSquare(private val shape: Shape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = min(size.width, size.height)
        val path = Path().apply {
            addOutline(shape.createOutline(Size(side, side), layoutDirection, density))
            translate(Offset((size.width - side) / 2, (size.height - side) / 2))
        }
        return Outline.Generic(path)
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
            modifier = Modifier.testTag(TapBurstTestTag),
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
            milestone = sampleMilestone,
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

private val sampleMilestone =
    CounterMilestoneState(MilestoneState(valueLabel = "50", remainingLabel = "8"), fraction = 17f / 25f)
