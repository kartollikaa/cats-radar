package dev.catsradar.data.platform

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dev.catsradar.domain.platform.GallerySaver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GALLERY_SUBDIRECTORY = "Cats Radar"

class MediaStoreGallerySaver(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GallerySaver {

    @Suppress("TooGenericExceptionCaught") // whatever fails, the half-written pending row has to go
    override suspend fun save(sourceUri: String, displayName: String): String? = withContext(ioDispatcher) {
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$GALLERY_SUBDIRECTORY",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore refused the insert")
            try {
                context.openPhotoStream(sourceUri).use { source ->
                    resolver.openOutputStream(target)?.use(source::copyTo) ?: error("no output stream")
                }
                resolver.update(
                    target,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null
                )
                target.toString()
            } catch (failure: Throwable) {
                // A half-written pending item would sit in the gallery forever, invisible and undeletable.
                resolver.delete(target, null, null)
                throw failure
            }
        }.getOrNull()
    }
}
