package dev.catsradar.ui.coat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

/** A cat's face in the colours and markings of [coat], centred in whatever space it is given. */
@Composable
internal fun CatFace(
    coat: CoatOption,
    modifier: Modifier = Modifier,
    rim: Color = MaterialTheme.colorScheme.faceRim()
) {
    val bitmap = ImageBitmap.imageResource(coat.faceResource())
    val rimFilter = ColorFilter.tint(rim)
    Canvas(modifier) {
        val side = size.minDimension.toInt()
        val destination = IntSize(side, side)
        val origin = IntOffset(((size.width - side) / 2).toInt(), ((size.height - side) / 2).toInt())
        val rimRadius = FaceRimWidth.toPx() / 2
        for (x in -1..1) {
            for (y in -1..1) {
                if (x == 0 && y == 0) continue
                translate(left = x * rimRadius, top = y * rimRadius) {
                    drawImage(
                        image = bitmap,
                        dstOffset = origin,
                        dstSize = destination,
                        colorFilter = rimFilter,
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
        }
        drawImage(image = bitmap, dstOffset = origin, dstSize = destination, filterQuality = FilterQuality.Medium)
    }
}

internal fun CoatOption.faceResource(): Int = when (this) {
    CoatOption.GINGER -> R.drawable.cat_face_ginger
    CoatOption.GINGER_WHITE -> R.drawable.cat_face_ginger_white
    CoatOption.WHITE -> R.drawable.cat_face_white
    CoatOption.TRICOLOR_MOSTLY_WHITE -> R.drawable.cat_face_tricolor_mostly_white
    CoatOption.TRICOLOR_LITTLE_WHITE -> R.drawable.cat_face_tricolor_little_white
    CoatOption.BROWN -> R.drawable.cat_face_brown
    CoatOption.BROWN_WHITE -> R.drawable.cat_face_brown_white
    CoatOption.GREY -> R.drawable.cat_face_grey
    CoatOption.GREY_WHITE -> R.drawable.cat_face_grey_white
    CoatOption.BLACK -> R.drawable.cat_face_black
    CoatOption.BLACK_WHITE -> R.drawable.cat_face_black_white
}

@OptIn(ExperimentalLayoutApi::class)
@ThemePreviews
@Composable
private fun CatFacePreview() {
    CatsRadarTheme {
        FlowRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CoatOption.entries.forEach { CatFace(coat = it, modifier = Modifier.size(48.dp)) }
        }
    }
}
