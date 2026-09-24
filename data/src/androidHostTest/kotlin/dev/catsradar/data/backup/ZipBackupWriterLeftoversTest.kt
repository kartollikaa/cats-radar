package dev.catsradar.data.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.domain.backup.BackupContents
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipFile
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val DOCUMENTS_AUTHORITY = "dev.catsradar.test.documents"
private const val BYTES_BEFORE_THE_DISK_FILLS = 512

/** What a failed export leaves where the user asked for the archive: nothing. */
@RunWith(AndroidJUnit4::class)
class ZipBackupWriterLeftoversTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)
    private val documents = Robolectric.setupContentProvider(RecordingDocuments::class.java, DOCUMENTS_AUTHORITY)
    private val document = "content://$DOCUMENTS_AUTHORITY/document/cats-radar-2026-09-24.zip"

    private fun writer(openTarget: (String) -> OutputStream?) = ZipBackupWriter(
        context = context,
        photoStorage = photoStorage,
        deviceIdProvider = StubDeviceId(),
        clock = FixedClock(Epoch),
        appVersion = "1.0",
        openTarget = openTarget,
    )

    private val withPhoto = BackupContents(encounters = listOf(photoCat("cat.jpg")))

    @Test
    fun anExportThatFailsPartWayIntoADocumentAsksForTheDocumentToBeDeleted() = runTest {
        photoStorage.prepare("cat.jpg").writeBytes(Random(3).nextBytes(64 * 1024))

        val written = writer { DiskFillsAfter(BYTES_BEFORE_THE_DISK_FILLS, ByteArrayOutputStream()) }
            .write(document, withPhoto)

        assertFalse(written)
        assertEquals(listOf(document), documents.deleted)
    }

    @Test
    fun anExportThatCannotOpenItsDocumentAsksForTheEmptyDocumentToBeDeleted() = runTest {
        val written = writer { null }.write(document, BackupContents())

        assertFalse(written)
        assertEquals(listOf(document), documents.deleted)
    }

    @Test
    fun anExportThatFailsPartWayIntoAFileLeavesNoFileThere() = runTest {
        photoStorage.prepare("cat.jpg").writeBytes(Random(3).nextBytes(64 * 1024))
        val target = File(temporaryFolder.root, "backup.zip")

        val written = writer { DiskFillsAfter(BYTES_BEFORE_THE_DISK_FILLS, FileOutputStream(it)) }
            .write(target.path, withPhoto)

        assertFalse(written)
        assertFalse(target.exists(), "a half-written archive was left behind")
    }

    @Test
    fun aPhotoTheWriterCannotReadIsLeftOutRatherThanArchivedEmpty() = runTest {
        photoStorage.prepare("cat.jpg").apply { writeText("jpeg bytes") }.setReadable(false)
        val target = File(temporaryFolder.root, "backup.zip")

        assertTrue(writer { FileOutputStream(it) }.write(target.path, withPhoto))

        val entries = ZipFile(target).use { zip -> zip.entries().toList().map { it.name } }
        assertFalse("${PHOTOS_PREFIX}cat.jpg" in entries, "an unreadable photo was archived as an empty entry")
    }
}

private class DiskFillsAfter(private val budget: Int, sink: OutputStream) : FilterOutputStream(sink) {
    private var written = 0

    override fun write(b: Int) {
        if (++written > budget) throw IOException("No space left on device")
        out.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        repeat(len) { write(b[off + it].toInt()) }
    }
}

/** Stands in for a documents provider: the one call a failed export makes is to delete the document. */
private class RecordingDocuments : ContentProvider() {
    val deleted = mutableListOf<String>()

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method == "android:deleteDocument") {
            @Suppress("DEPRECATION") // the typed overload needs API 33; the provider is called on any
            deleted += extras?.getParcelable<Uri>("uri").toString()
        }
        return Bundle.EMPTY
    }

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
