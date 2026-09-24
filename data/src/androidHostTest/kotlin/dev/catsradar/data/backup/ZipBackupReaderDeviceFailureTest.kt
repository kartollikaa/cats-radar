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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
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
    fun makeTheAppDirectoriesWritableAgain() {
        context.filesDir.setWritable(true)
        File(context.filesDir, "photos").setWritable(true)
    }

    private fun validArchive(): File = File(temporaryFolder.root, "backup.zip").apply {
        writeArchive(
            MANIFEST_ENTRY to VALID_MANIFEST,
            ENCOUNTERS_ENTRY to catWithPhotos("cat.jpg"),
            "${PHOTOS_PREFIX}cat.jpg" to "jpeg bytes ".repeat(4096),
        )
    }

    @Test
    fun aSourceWhoseStreamBreaksPartWayFailsTheReadRatherThanBeingCalledUnreadable() = runTest {
        val bytes = validArchive().readBytes()
        val dropsHalfway = ZipBackupReader(context, photoStorage) {
            ConnectionDropsAfter(bytes.size / 2, ByteArrayInputStream(bytes))
        }

        assertFailsWith<IOException> { dropsHalfway.read("content://cloud/document/backup.zip") }
    }

    @Test
    fun aSourceThatGivesNoStreamFailsTheReadRatherThanBeingCalledUnreadable() = runTest {
        val noStream = ZipBackupReader(context, photoStorage) { null }

        assertFailsWith<IOException> { noStream.read("content://cloud/document/backup.zip") }
    }

    @Test
    fun aPhotoThisDeviceCannotPutInPlaceFailsTheRead() = runTest {
        val archive = validArchive()
        File(context.filesDir, "photos").apply { mkdirs() }.setWritable(false)
        assumeFalse("a process running as root writes anyway", File(context.filesDir, "photos").canWrite())

        assertFailsWith<IOException> { reader.read(archive.path) }
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

        assertFailsWith<IOException> { reader.read(archive.path) }
        context.filesDir.setWritable(true)
        assertFalse(photoStorage.fileFor("cat.jpg").exists(), "a photo was left behind")
    }
}

private class ConnectionDropsAfter(private val budget: Int, source: InputStream) : FilterInputStream(source) {
    private var served = 0

    override fun read(): Int = if (++served > budget) throw IOException("Connection reset") else super.read()

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (served >= budget) throw IOException("Connection reset")
        return super.read(b, off, minOf(len, budget - served)).also { if (it > 0) served += it }
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
