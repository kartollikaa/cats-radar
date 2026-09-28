package dev.catsradar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

const val ShareBarTestTag = "share-bar"
const val ShareBarFillTestTag = "share-bar-fill"

private val ShareBarHeight = 4.dp

/** One colour of a share bar and its [weight] against the other parts' weights. */
internal data class BarPart(val color: Color, val weight: Float)

private val ColorScheme.shareBarTrack: Color get() = surfaceContainerHighest

/** [share] of a whole, from 0 to 1, filled in [color]. */
@Composable
internal fun ShareBar(share: Float, color: Color, modifier: Modifier = Modifier) {
    ShareBarFrame(share = share, fill = SolidColor(color), modifier = modifier)
}

/**
 * [share] of a whole, from 0 to 1, filled with [parts] blended into each other, each at least [minContrast]
 * apart from the track; see [blendStops].
 */
@Composable
internal fun ShareBar(share: Float, parts: ImmutableList<BarPart>, minContrast: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val track = colors.shareBarTrack
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fill = remember(parts, track, colors.onSurface, minContrast, rightToLeft) {
        blendedBrush(parts, track, colors.onSurface, minContrast, rightToLeft)
    }
    ShareBarFrame(share = share, fill = fill, modifier = modifier)
}

@Composable
private fun ShareBarFrame(share: Float, fill: Brush, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .testTag(ShareBarTestTag)
            .fillMaxWidth()
            .height(ShareBarHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.shareBarTrack),
    ) {
        // At least a round dot, so a row holding one cat among hundreds never reads as holding none.
        Box(
            modifier = Modifier
                .testTag(ShareBarFillTestTag)
                .widthIn(min = ShareBarHeight)
                .fillMaxWidth(share)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(fill),
        )
    }
}

@ThemePreviews
@Composable
private fun ShareBarPreview() {
    CatsRadarTheme {
        val colors = MaterialTheme.colorScheme
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ShareBar(share = 0.6f, color = colors.primary)
            ShareBar(
                share = 0.8f,
                parts = persistentListOf(BarPart(colors.tertiary, 2f), BarPart(colors.surface, 1f)),
                minContrast = 1.3f,
            )
        }
    }
}
