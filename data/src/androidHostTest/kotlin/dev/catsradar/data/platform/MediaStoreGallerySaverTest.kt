package dev.catsradar.data.platform

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class MediaStoreGallerySaverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun fixture() = PhotoFixtures.copyTo(temporaryFolder.root, PhotoFixtures.SMALL_NO_EXIF).path

    @Test
    fun aSavedPhotoComesBackWithTheGalleryUriItWasGiven() = runTest {
        val uri = MediaStoreGallerySaver(context).save(fixture(), "cat-1.jpg")

        assertNotNull(uri)
    }

    @Test
    fun aGalleryThatRefusesTheInsertYieldsNoUriInsteadOfThrowing() = runTest {
        ShadowContentResolver.registerProviderInternal(MEDIA_AUTHORITY, RefusingProvider())

        assertNull(MediaStoreGallerySaver(context).save(fixture(), "cat-2.jpg"))
    }

    @Test
    fun aSourceThatCannotBeReadLeavesNoPendingItemBehind() = runTest {
        val resolver = context.contentResolver
        val before = resolver.countImages()

        val uri = MediaStoreGallerySaver(context).save("${temporaryFolder.root}/nothing-here.jpg", "cat-3.jpg")

        assertNull(uri)
        // A pending row that nothing ever finishes writing would sit in the user's gallery
        // invisible and undeletable.
        kotlin.test.assertEquals(before, resolver.countImages())
    }

    private fun ContentResolver.countImages(): Int =
        Shadows.shadowOf(this).let { query(IMAGES, null, null, null, null)?.use { cursor -> cursor.count } ?: 0 }

    /** Stands in for a MediaStore that will not take the photo — a full volume, or no volume. */
    private class RefusingProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null
        override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
        override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null
    }

    private companion object {
        const val MEDIA_AUTHORITY = "media"
        val IMAGES: Uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
}
