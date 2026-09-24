package dev.catsradar.data.platform

import android.content.ContentResolver
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
        val item = Uri.parse(uri).takeIf { it.scheme == ContentResolver.SCHEME_CONTENT } ?: return@withContext false
        runCatching {
            context.contentResolver.query(item, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
    }
}
