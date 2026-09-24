package dev.catsradar.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.coat.faceRim
import dev.catsradar.ui.coat.look
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

private const val FULL_TURN = 360f

/** A cat's dot in its coat's colours: the fur over the top, each marking a slice below it, a rim around. */
internal class CoatDotPainter(
    private val shares: List<ColourShare>,
    private val rim: Color,
    private val rimWidth: Dp,
) : Painter() {

    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val rimPx = rimWidth.toPx()
        val fill = size.minDimension / 2 - rimPx
        val corner = center - Offset(fill, fill)
        val box = Size(fill * 2, fill * 2)
        drawCircle(shares.first().colour, radius = fill)
        // Laid from nine o'clock back to three, so the markings read left to right in the order given.
        var end = FULL_TURN / 2
        shares.drop(1).forEach { marking ->
            val sweep = FULL_TURN * marking.share.toFloat()
            drawArc(
                marking.colour,
                startAngle = end - sweep,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = corner,
                size = box,
            )
            end -= sweep
        }
        drawCircle(rim, radius = fill + rimPx / 2, style = Stroke(rimPx))
    }
}

@ThemePreviews
@Composable
private fun CoatDotPainterPreview() {
    CatsRadarTheme {
        Surface {
            val rim = MaterialTheme.colorScheme.faceRim()
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoatOption.entries.forEach { coat ->
                    Image(
                        painter = CoatDotPainter(coat.look().shares(), rim, RimWidth),
                        contentDescription = null,
                        modifier = Modifier.size(DotSize),
                    )
                }
            }
        }
    }
}
