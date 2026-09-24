package dev.catsradar.data.platform

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dev.catsradar.domain.platform.GalleryItems
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreGalleryItems(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GalleryItems {

    // An app sees only the MediaStore rows it owns, and the trash hides a deleted one: no row is gone.
    override suspend fun exists(uri: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            context.contentResolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
    }
}
