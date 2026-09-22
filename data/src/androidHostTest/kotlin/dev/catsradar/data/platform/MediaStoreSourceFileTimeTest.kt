package dev.catsradar.data.platform

import android.content.ContentProvider
import android.content.ContentValues
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
import kotlin.test.assertNull
import kotlin.time.Instant

private const val AUTHORITY = "test_picker"
private val PICKED: Uri = Uri.parse("content://$AUTHORITY/media/42")

@RunWith(AndroidJUnit4::class)
class MediaStoreSourceFileTimeTest {

    private val subject = MediaStoreSourceFileTime(ApplicationProvider.getApplicationContext())

    private fun register(provider: ContentProvider) =
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)

    @Test
    fun aPickerThatReportsDateTakenGivesThatInstant() = runTest {
        register(ColumnProvider(mapOf(MediaStore.MediaColumns.DATE_TAKEN to TAKEN_MILLIS)))

        assertEquals(Instant.fromEpochMilliseconds(TAKEN_MILLIS), subject.createdAt(PICKED.toString()))
    }

    @Test
    fun dateAddedIsReadAsSecondsNotMilliseconds() = runTest {
        register(ColumnProvider(mapOf(MediaStore.MediaColumns.DATE_ADDED to ADDED_SECONDS)))

        assertEquals(Instant.fromEpochSeconds(ADDED_SECONDS), subject.createdAt(PICKED.toString()))
    }

    @Test
    fun dateTakenWinsOverTheWeakerColumns() = runTest {
        register(
            ColumnProvider(
                mapOf(
                    MediaStore.MediaColumns.DATE_TAKEN to TAKEN_MILLIS,
                    MediaStore.MediaColumns.DATE_ADDED to ADDED_SECONDS,
                    MediaStore.MediaColumns.DATE_MODIFIED to ADDED_SECONDS,
                ),
            ),
        )

        assertEquals(Instant.fromEpochMilliseconds(TAKEN_MILLIS), subject.createdAt(PICKED.toString()))
    }

    @Test
    fun dateModifiedIsTheLastResort() = runTest {
        register(ColumnProvider(mapOf(MediaStore.MediaColumns.DATE_MODIFIED to ADDED_SECONDS)))

        assertEquals(Instant.fromEpochSeconds(ADDED_SECONDS), subject.createdAt(PICKED.toString()))
    }

    @Test
    fun aProviderThatThrowsOnAnUnknownColumnLeavesTheDateAbsentRatherThanFailingTheImport() = runTest {
        register(ThrowingProvider())

        assertNull(subject.createdAt(PICKED.toString()))
    }

    @Test
    fun aProviderWithNoRowAtAllYieldsNoDate() = runTest {
        register(ColumnProvider(emptyMap()))

        assertNull(subject.createdAt(PICKED.toString()))
    }

    @Test
    fun aNullColumnValueIsNotReadAsTheEpoch() = runTest {
        register(NullValueProvider())

        assertNull(subject.createdAt(PICKED.toString()))
    }

    /** Answers only for the columns it was given, the way a Photo Picker URI's narrow projection does. */
    private class ColumnProvider(private val values: Map<String, Long>) : ReadOnlyProvider() {
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            val column = projection?.singleOrNull() ?: return null
            return MatrixCursor(arrayOf(column)).apply {
                values[column]?.let { addRow(arrayOf<Any>(it)) }
            }
        }
    }

    private class NullValueProvider : ReadOnlyProvider() {
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(arrayOf(projection!!.single())).apply { addRow(arrayOfNulls<Any>(1)) }
    }

    private class ThrowingProvider : ReadOnlyProvider() {
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = throw IllegalArgumentException("Invalid column ${projection?.joinToString()}")
    }

    private companion object {
        const val TAKEN_MILLIS = 1_756_000_000_000L
        const val ADDED_SECONDS = 1_755_000_000L
    }
}

private abstract class ReadOnlyProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
