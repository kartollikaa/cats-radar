package dev.catsradar.data.platform

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import dev.catsradar.domain.platform.GalleryItemLocator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
private const val ON_DEVICE_PICKER_AUTHORITY = "com.android.providers.media.photopicker"
private val PICKER_SEGMENTS = setOf("picker", "picker_get_content")
private const val PER_USER_UID_RANGE = 100_000

class MediaStoreItemLocator(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GalleryItemLocator {

    override suspend fun locate(pickedUri: String): String? = withContext(ioDispatcher) {
        val uri = Uri.parse(pickedUri)
        when (uri.authority) {
            MediaStore.AUTHORITY -> fromMediaStore(uri)
            MEDIA_DOCUMENTS_AUTHORITY -> runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()?.toString()
            else -> null
        }
    }

    private fun fromMediaStore(uri: Uri): String? {
        val segments = uri.pathSegments
        val isItem = segments.size == 4 && segments[1] == "images" && segments[2] == "media" &&
            segments[3].toLongOrNull() != null
        return when {
            segments.firstOrNull() in PICKER_SEGMENTS -> fromPicker(segments)
            isItem -> uri.toString()
            else -> null
        }
    }

    // Not public API: picker/<user>/<provider>/media/<id>, whose <id> is the MediaStore _id when the
    // provider is the on-device one (AOSP PickerUriResolver). A cloud provider's <id> is its own.
    private fun fromPicker(segments: List<String>): String? {
        val onThisProfile = segments.size == 5 && segments[1] == currentUser() && segments[3] == "media"
        if (!onThisProfile || segments[2] != ON_DEVICE_PICKER_AUTHORITY) return null
        val id = segments[4].toLongOrNull() ?: return null
        return ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), id)
            .toString()
    }

    private fun currentUser(): String = (Process.myUid() / PER_USER_UID_RANGE).toString()
}
