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
private const val PER_USER_UID_RANGE = 100_000

// Not public API (AOSP PickerUriResolver): <user>, <provider>, <id>, where <id> is the MediaStore _id
// when <provider> is the on-device one, and a cloud provider's own id otherwise.
private val PickerPath = Regex("""/picker(?:_get_content)?/(\d+)/([^/]+)/media/(\d+)""")
private val ImagePath = Regex("""/[^/]+/images/media/\d+""")

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
        val path = uri.path.orEmpty()
        val picked = PickerPath.matchEntire(path)?.destructured
        return when {
            picked != null -> fromPicker(picked)
            ImagePath.matches(path) -> uri.toString()
            else -> null
        }
    }

    private fun fromPicker(picked: MatchResult.Destructured): String? {
        val (user, provider, id) = picked
        val onThisPhone = user == currentUser() && provider == ON_DEVICE_PICKER_AUTHORITY
        val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        return id.toLongOrNull()?.takeIf { onThisPhone }?.let { ContentUris.withAppendedId(images, it).toString() }
    }

    private fun currentUser(): String = (Process.myUid() / PER_USER_UID_RANGE).toString()
}
