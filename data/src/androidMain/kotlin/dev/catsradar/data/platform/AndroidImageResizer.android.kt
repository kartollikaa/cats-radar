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

    override suspend fun store(sourceUri: String, baseName: String): StoredPhoto? =
        withContext(ioDispatcher) {
            val source = decode(sourceUri) ?: return@withContext null
            val turn = uprightTurn(sourceUri)
            try {
                val photoPath = "$baseName.jpg"
                // No copy is worth keeping without the photo itself, so a failure here fails the call.
                if (!source.writeScaled(Tuning.PHOTO_MAX_SIDE, turn, photoStorage.prepare(photoPath))) {
                    return@withContext null
                }
                val thumbPath = "$baseName$THUMB_SUFFIX"
                val thumbWritten = source.writeScaled(Tuning.THUMB_SIZE, turn, photoStorage.prepare(thumbPath))
                StoredPhoto(photoPath = photoPath, thumbPath = thumbPath.takeIf { thumbWritten })
            } finally {
                source.recycle()
            }
        }

    private fun decode(sourceUri: String): Bitmap? =
        runCatching { context.openPhotoStream(sourceUri).use(BitmapFactory::decodeStream) }.getOrNull()

    // BitmapFactory ignores EXIF Orientation, and the copies carry no EXIF, so the turn goes into the pixels.
    private fun uprightTurn(sourceUri: String): Matrix = Matrix().apply {
        val exif = runCatching { context.openPhotoStream(sourceUri).use(::ExifInterface) }.getOrNull()
            ?: return@apply
        // ExifInterface's rotation degrees assume the flip has already been applied.
        if (exif.isFlipped) postScale(-1f, 1f)
        postRotate(exif.rotationDegrees.toFloat())
    }

    private fun Bitmap.writeScaled(maxSide: Int, turn: Matrix, destination: File): Boolean = runCatching {
        val target = scaleToFit(width, height, maxSide)
        val scaled = if (target.width == width && target.height == height) {
            this
        } else {
            Bitmap.createScaledBitmap(this, target.width, target.height, true)
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
            if (scaled !== this) scaled.recycle()
        }
    }.getOrDefault(false)

    private companion object {
        const val THUMB_SUFFIX = "_thumb.jpg"
    }
}
