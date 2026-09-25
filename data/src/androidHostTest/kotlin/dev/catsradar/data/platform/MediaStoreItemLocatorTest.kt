package dev.catsradar.data.platform

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The picker URIs are verbatim what an API 37 photo picker handed over for MediaStore items 17 and 18.
@RunWith(AndroidJUnit4::class)
class MediaStoreItemLocatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locator = MediaStoreItemLocator(context)

    @Test
    fun aPhotoImportedThroughGetContentIsTheMediaStoreItemItNames() = runTest {
        val picked = "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/17"

        assertEquals("content://media/external/images/media/17", locator.locate(picked))
    }

    @Test
    fun aPhotoChosenInThePhotoPickerIsTheMediaStoreItemItNames() = runTest {
        val picked = "content://media/picker/0/com.android.providers.media.photopicker/media/18"

        assertEquals("content://media/external/images/media/18", locator.locate(picked))
    }

    @Test
    fun aMediaStoreItemHandedOverAsItselfIsKept() = runTest {
        assertEquals(MEDIA_ITEM, locator.locate(MEDIA_ITEM))
        assertEquals(
            "content://media/external_primary/images/media/42",
            locator.locate("content://media/external_primary/images/media/42"),
        )
    }

    @Test
    fun aFilesAppImageIsTheItemAndroidsOwnConversionNames() = runTest {
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, Converting(answer = Uri.parse(MEDIA_ITEM)))

        assertEquals(MEDIA_ITEM, locator.locate("content://$MEDIA_DOCUMENTS/document/image%3A42"))
    }

    @Test
    fun aFilesAppImageAndroidCannotConvertHasNoItem() = runTest {
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, Converting(answer = null))

        assertNull(locator.locate("content://$MEDIA_DOCUMENTS/document/image%3A42"))
    }

    @Test
    fun aCloudOnlyPhotoHasNoItemOnThisDevice() = runTest {
        assertNull(locator.locate("content://media/picker/0/com.google.android.apps.photos.cloudpicker/media/17"))
    }

    @Test
    fun aPhotoFromAnotherProfileHasNoItemForThisOne() = runTest {
        assertNull(locator.locate("content://media/picker/10/com.android.providers.media.photopicker/media/17"))
    }

    @Test
    fun anotherAppsProviderHasNoItem() = runTest {
        assertNull(locator.locate("content://com.google.android.apps.photos.contentprovider/-1/1/abc/ORIGINAL/NONE/1"))
    }

    @Test
    fun aFilePathOrGarbageHasNoItem() = runTest {
        assertNull(locator.locate("file:///sdcard/Pictures/cat.jpg"))
        assertNull(locator.locate("not a uri"))
        assertNull(locator.locate("content://media/picker/0/com.android.providers.media.photopicker/media/not-a-number"))
    }

    /** MediaStore answering the document-to-item conversion `MediaStore.getMediaUri` asks it for with [answer]. */
    private class Converting(private val answer: Uri?) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? =
            answer?.let { Bundle().apply { putParcelable("uri", it) } }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
        override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null
    }

    private companion object {
        const val MEDIA_DOCUMENTS = "com.android.providers.media.documents"
        const val MEDIA_ITEM = "content://media/external/images/media/42"
    }
}
