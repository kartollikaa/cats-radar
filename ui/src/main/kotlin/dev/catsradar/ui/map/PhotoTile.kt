package dev.catsradar.ui.map

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/** A cat's photo on the map: a rounded square inside a rim, measured in pixels of the bitmap drawn. */
internal data class PhotoTile(val size: Int, val corner: Float, val rim: Float, val rimColor: Color)

/** The middle square of the thumbnail at [path] drawn as this tile, or null when the file is no image. */
internal suspend fun PhotoTile.draw(path: String, decoder: CoroutineDispatcher = Dispatchers.IO): ImageBitmap? =
    withContext(decoder) { BitmapFactory.decodeFile(path)?.asImageBitmap()?.let(::around) }

private fun PhotoTile.around(photo: ImageBitmap): ImageBitmap {
    val side = size.toFloat()
    val square = middleSquare(photo, size)
    val tile = ImageBitmap(size, size)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(tile), Size(side, side)) {
        drawRoundRect(ShaderBrush(ImageShader(square)), cornerRadius = CornerRadius(corner))
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

private fun middleSquare(photo: ImageBitmap, size: Int): ImageBitmap {
    val crop = min(photo.width, photo.height)
    val square = ImageBitmap(size, size)
    Canvas(square).drawImageRect(
        photo,
        srcOffset = IntOffset((photo.width - crop) / 2, (photo.height - crop) / 2),
        srcSize = IntSize(crop, crop),
        dstSize = IntSize(size, size),
        paint = Paint().apply { filterQuality = FilterQuality.High },
    )
    return square
}
