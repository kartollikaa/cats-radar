package dev.catsradar.data.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
            val source = decode(sourceUri) ?: return@withContext null
            try {
                val photoPath = "$encounterId.jpg"
                // No copy is worth keeping without the photo itself, so a failure here fails the call.
                if (!source.writeScaled(Tuning.PHOTO_MAX_SIDE, photoStorage.prepare(photoPath))) {
                    return@withContext null
                }
                val thumbPath = "$encounterId$THUMB_SUFFIX"
                val thumbWritten = source.writeScaled(Tuning.THUMB_SIZE, photoStorage.prepare(thumbPath))
                StoredPhoto(photoPath = photoPath, thumbPath = thumbPath.takeIf { thumbWritten })
            } finally {
                source.recycle()
            }
        }

    private fun decode(sourceUri: String): Bitmap? =
        runCatching { context.openPhotoStream(sourceUri).use(BitmapFactory::decodeStream) }.getOrNull()

    private fun Bitmap.writeScaled(maxSide: Int, destination: File): Boolean = runCatching {
        val target = scaleToFit(width, height, maxSide)
        val scaled = if (target.width == width && target.height == height) {
            this
        } else {
            Bitmap.createScaledBitmap(this, target.width, target.height, true)
        }
        try {
            // JPEG, and no EXIF is carried over: the app's own copy never republishes the
            // original's GPS.
            destination.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, Tuning.PHOTO_QUALITY, out)
            }
        } finally {
            if (scaled !== this) scaled.recycle()
        }
    }.getOrDefault(false)

    private companion object {
        const val THUMB_SUFFIX = "_thumb.jpg"
    }
}
