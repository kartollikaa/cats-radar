package dev.catsradar.data.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.photo.scaleToFit
import dev.catsradar.domain.platform.ImageResizer
import dev.catsradar.domain.platform.StoredPhoto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidImageResizer(
    private val context: Context,
    private val photoStorage: AndroidPhotoStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ImageResizer {

    override suspend fun store(sourceUri: String, encounterId: String): StoredPhoto? =
        withContext(ioDispatcher) {
            val source = context.decodeShrunk(sourceUri, Tuning.PHOTO_MAX_SIDE) ?: return@withContext null
            val turn = uprightTurn(sourceUri)
            try {
                val photoPath = "$encounterId.jpg"
                // No copy is worth keeping without the photo itself, so a failure here fails the call.
                if (!source.writeScaled(Tuning.PHOTO_MAX_SIDE, turn, photoStorage.prepare(photoPath))) {
                    return@withContext null
                }
                val thumbPath = "$encounterId$THUMB_SUFFIX"
                val thumbWritten = source.writeScaled(Tuning.THUMB_SIZE, turn, photoStorage.prepare(thumbPath))
                StoredPhoto(photoPath = photoPath, thumbPath = thumbPath.takeIf { thumbWritten })
            } finally {
                source.bitmap.recycle()
            }
        }

    // BitmapFactory ignores EXIF Orientation, and the copies carry no EXIF, so the turn goes into the pixels.
    private fun uprightTurn(sourceUri: String): Matrix = Matrix().apply {
        val exif = runCatching { context.openPhotoStream(sourceUri).use(::ExifInterface) }.getOrNull()
            ?: return@apply
        // ExifInterface's rotation degrees assume the flip has already been applied.
        if (exif.isFlipped) postScale(-1f, 1f)
        postRotate(exif.rotationDegrees.toFloat())
    }

    private fun DecodedPhoto.writeScaled(maxSide: Int, turn: Matrix, destination: File): Boolean = runCatching {
        // Not the bitmap's size: the decoder rounds the sides of a shrunk one.
        val target = scaleToFit(fileWidth, fileHeight, maxSide)
        val scaled = if (target.width == bitmap.width && target.height == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, target.width, target.height, true)
        }
        try {
            val upright = if (turn.isIdentity) {
                scaled
            } else {
                Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, turn, true)
            }
            try {
                // JPEG, and no EXIF is carried over: the app's own copy never republishes the
                // original's GPS.
                destination.outputStream().use { out ->
                    upright.compress(Bitmap.CompressFormat.JPEG, Tuning.PHOTO_QUALITY, out)
                }
            } finally {
                if (upright !== scaled) upright.recycle()
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }.getOrDefault(false)

    private companion object {
        const val THUMB_SUFFIX = "_thumb.jpg"
    }
}

/** [bitmap] may be shrunk; neither it nor [fileWidth] x [fileHeight] has the EXIF turn applied. */
internal class DecodedPhoto(val bitmap: Bitmap, val fileWidth: Int, val fileHeight: Int)

internal fun Context.decodeShrunk(sourceUri: String, minLongestSide: Int): DecodedPhoto? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openPhotoStream(sourceUri).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    val shrunk = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), minLongestSide)
    }
    openPhotoStream(sourceUri).use { BitmapFactory.decodeStream(it, null, shrunk) }
        ?.let { DecodedPhoto(it, bounds.outWidth, bounds.outHeight) }
}.getOrNull()

/**
 * The largest power of two that leaves [longestSide] at least [minLongestSide]. Only a power of two is shrunk
 * inside the JPEG decoder, which averages; BitmapFactory honours any other value by skipping pixels.
 */
internal fun sampleSizeFor(longestSide: Int, minLongestSide: Int): Int {
    require(minLongestSide > 0) { "minLongestSide must be positive, was $minLongestSide" }
    var sampleSize = 1
    while (longestSide / (sampleSize * 2) >= minLongestSide) sampleSize *= 2
    return sampleSize
}
