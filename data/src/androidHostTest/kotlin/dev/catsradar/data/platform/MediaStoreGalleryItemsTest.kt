package dev.catsradar.data.platform

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MediaStoreGalleryItemsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val items = MediaStoreGalleryItems(context)

    @Test
    fun anItemMediaStoreReturnsARowForExists() = runTest {
        val gallery = register(Gallery(rows = setOf(SAVED)))

        assertTrue(items.exists(SAVED))
        assertEquals(listOf(Uri.parse(SAVED)), gallery.queried)
    }

    @Test
    fun anItemMediaStoreReturnsNoRowForIsGone() = runTest {
        register(Gallery(rows = emptySet()))

        assertFalse(items.exists(SAVED))
    }

    @Test
    fun aQueryAnsweredWithNoCursorIsGone() = runTest {
        register(Gallery(rows = setOf(SAVED), answersWithNoCursor = true))

        assertFalse(items.exists(SAVED))
    }

    @Test
    fun aQueryTheProviderRefusesIsGone() = runTest {
        register(Gallery(rows = setOf(SAVED), refuses = true))

        assertFalse(items.exists(SAVED))
    }

    @Test
    fun aStringThatIsNotAContentUriIsGoneWithoutReachingTheGallery() = runTest {
        val gallery = register(Gallery(rows = setOf(SAVED)))

        assertFalse(items.exists("not a uri"))
        assertEquals(emptyList(), gallery.queried)
    }

    private fun register(gallery: Gallery): Gallery {
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, gallery)
        return gallery
    }

    /** A MediaStore holding [rows]; it can answer with no cursor or refuse the caller outright. */
    private class Gallery(
        private val rows: Set<String>,
        private val answersWithNoCursor: Boolean = false,
        private val refuses: Boolean = false,
    ) : ContentProvider() {
        val queried = mutableListOf<Uri>()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            queried += uri
            if (refuses) throw SecurityException("not the owner of $uri")
            if (answersWithNoCursor) return null
            return MatrixCursor(arrayOf(MediaStore.MediaColumns._ID)).apply {
                if (uri.toString() in rows) addRow(arrayOf<Any>(uri.lastPathSegment.orEmpty()))
            }
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
        override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null
    }

    private companion object {
        const val SAVED = "content://media/external/images/media/42"
    }
}
