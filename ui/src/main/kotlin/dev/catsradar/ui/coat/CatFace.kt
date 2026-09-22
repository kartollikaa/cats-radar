package dev.catsradar.ui.coat

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption

// Every shape below is drawn on a 40-unit square and scaled to whatever size the face is given.
private const val FaceUnits = 40f
private val RimWidth = 1.2.dp

private fun svg(pathData: String): Path = PathParser().parsePathString(pathData).toPath()

private val Head = svg(
    "M20 36C11 36 6 31 6 24C6 19 8 15 10 13L9 3L17 9.5C19 9 21 9 23 9.5L31 3L30 13C32 15 34 19 34 24" +
        "C34 31 29 36 20 36Z",
)
private val Muzzle = svg(
    "M20 36C15 36 12.5 33.5 12.5 30.5C12.5 27.5 15.5 25 18.6 25L20 15L21.4 25C24.5 25 27.5 27.5 27.5 30.5" +
        "C27.5 33.5 25 36 20 36Z",
)
private val LeftCrown = svg("M9 3L17 9.5C14.5 12.5 11 18 6.2 22.5C6.8 18.5 8.3 15 10 13Z")
private val RightCrown = svg("M31 3L23 9.5C25.5 12.5 29 18 33.8 22.5C33.2 18.5 31.7 15 30 13Z")
private val Nose = svg("M18.4 26.2H21.6L20 28.2Z")
private val NoseColor = Color(0xFFE88A9A)

private const val EyeRadiusX = 1.9f
private const val EyeRadiusY = 2.5f
private val LeftEye = Offset(14.5f, 21.5f)
private val RightEye = Offset(25.5f, 21.5f)

/** A cat's face in the colours and markings of [coat]. Carries no content description of its own. */
@Composable
internal fun CatFace(coat: CoatOption, modifier: Modifier = Modifier) {
    val look = coat.look()
    val rim = MaterialTheme.colorScheme.faceRim()
    Canvas(modifier) {
        val unit = size.minDimension / FaceUnits
        scale(scale = unit, pivot = Offset.Zero) {
            drawFace(look, rim, rimWidth = RimWidth.toPx() / unit)
        }
    }
}

private fun DrawScope.drawFace(look: CoatLook, rim: Color, rimWidth: Float) {
    drawPath(Head, look.fur)
    clipPath(Head) {
        look.leftCrown?.let { drawPath(LeftCrown, it) }
        look.rightCrown?.let { drawPath(RightCrown, it) }
        look.muzzle?.let { drawPath(Muzzle, it) }
    }
    listOf(LeftEye, RightEye).forEach { centre ->
        drawOval(
            color = look.eyes,
            topLeft = Offset(centre.x - EyeRadiusX, centre.y - EyeRadiusY),
            size = Size(EyeRadiusX * 2, EyeRadiusY * 2),
        )
    }
    drawPath(Nose, NoseColor)
    drawPath(Head, rim, style = Stroke(width = rimWidth))
}
