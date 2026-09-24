package dev.catsradar.data.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.platform.AndroidPhotoStorage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

private const val REFUSING_AUTHORITY = "dev.catsradar.test.refusing"

/** A failure on this device's side is not the archive's fault, so it is not reported as one. */
@RunWith(AndroidJUnit4::class)
class ZipBackupReaderDeviceFailureTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)
    private val reader = ZipBackupReader(context, photoStorage)

    @After
    fun makeTheAppDirectoryWritableAgain() {
        context.filesDir.setWritable(true)
    }

    @Test
    fun anArchiveTheAppMayNoLongerOpenFailsTheReadRatherThanBeingCalledUnreadable() = runTest {
        Robolectric.setupContentProvider(RefusingProvider::class.java, REFUSING_AUTHORITY)

        assertFailsWith<SecurityException> { reader.read("content://$REFUSING_AUTHORITY/document/backup.zip") }
    }

    @Test
    fun anArchiveThisDeviceHasNoRoomToUnpackFailsTheReadAndLeavesNoPhotoBehind() = runTest {
        val archive = File(temporaryFolder.root, "backup.zip").apply {
            writeArchive(
                MANIFEST_ENTRY to VALID_MANIFEST,
                ENCOUNTERS_ENTRY to catWithPhotos("cat.jpg"),
                "${PHOTOS_PREFIX}cat.jpg" to "jpeg bytes",
            )
        }
        context.filesDir.setWritable(false)
        assumeFalse("a process running as root writes anyway", context.filesDir.canWrite())

        assertFailsWith<java.io.IOException> { reader.read(archive.path) }
        context.filesDir.setWritable(true)
        assertFalse(photoStorage.fileFor("cat.jpg").exists(), "a photo was left behind")
    }
}

/** Stands in for a provider whose grant has gone: it refuses to open the document at all. */
private class RefusingProvider : ContentProvider() {
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        throw SecurityException("Permission Denial: reading $uri requires a grant")

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
