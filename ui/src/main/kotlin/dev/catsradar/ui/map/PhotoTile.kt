package dev.catsradar.ui.map

import android.content.Context
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import kotlin.math.min

/** A cat's photo on the map: a rounded square inside a rim, measured in pixels of the bitmap drawn. */
internal data class PhotoTile(val size: Int, val corner: Float, val rim: Float, val rimColor: Color)

/** The middle square of the thumbnail at [path] drawn as this tile, or null when the file is no image. */
internal suspend fun PhotoTile.draw(path: String, context: Context, imageLoader: ImageLoader): ImageBitmap? {
    val request = ImageRequest.Builder(context)
        .data(path)
        .size(size)
        .scale(Scale.FILL)
        .allowHardware(false)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .build()
    val result = imageLoader.execute(request) as? SuccessResult ?: return null
    return around(result.image.toBitmap().asImageBitmap())
}

private fun PhotoTile.around(photo: ImageBitmap): ImageBitmap {
    val tileSize = size
    val side = tileSize.toFloat()
    val tile = ImageBitmap(tileSize, tileSize)
    val crop = min(photo.width, photo.height)
    val outline = Path().apply {
        addRoundRect(RoundRect(0f, 0f, side, side, CornerRadius(corner)))
    }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(tile), Size(side, side)) {
        clipPath(outline) {
            drawImage(
                photo,
                srcOffset = IntOffset((photo.width - crop) / 2, (photo.height - crop) / 2),
                srcSize = IntSize(crop, crop),
                dstSize = IntSize(tileSize, tileSize),
                filterQuality = FilterQuality.High,
            )
        }
        val inset = rim / 2
        drawRoundRect(
            rimColor,
            topLeft = Offset(inset, inset),
            size = Size(side - rim, side - rim),
            cornerRadius = CornerRadius(corner - inset),
            style = Stroke(rim),
        )
    }
    return tile
}
