package dev.catsradar.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.domain.backup.BackupContents
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupRejection
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What reading an archive may and may not leave in the photo directory. */
@RunWith(AndroidJUnit4::class)
class ZipBackupReaderPhotosTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)

    private fun reader() = ZipBackupReader(context, photoStorage)

    private fun target(): String = File(temporaryFolder.root, "backup.zip").path

    private fun photoRestored(relativePath: String): Boolean = photoStorage.fileFor(relativePath).exists()

    @Test
    fun anArchiveFromANewerVersionLeavesNoPhotoBehind() = runTest {
        val path = target()
        File(path).writeArchive(
            MANIFEST_ENTRY to NEWER_MANIFEST,
            ENCOUNTERS_ENTRY to catWithPhotos("cat.jpg"),
            "${PHOTOS_PREFIX}cat.jpg" to "jpeg bytes",
        )

        assertEquals(BackupReadResult.Rejected(BackupRejection.TOO_NEW), reader().read(path))
        assertFalse(photoRestored("cat.jpg"), "a refused archive restored a photo")
    }

    @Test
    fun anArchiveWhoseRowsWillNotParseLeavesNoPhotoBehind() = runTest {
        val path = target()
        File(path).writeArchive(
            MANIFEST_ENTRY to VALID_MANIFEST,
            "${PHOTOS_PREFIX}cat.jpg" to "jpeg bytes",
            ENCOUNTERS_ENTRY to """[{"id":"a"}]""",
        )

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader().read(path))
        assertFalse(photoRestored("cat.jpg"), "a refused archive restored a photo")
    }

    @Test
    fun anArchiveCutOffInTheMiddleOfAPhotoLeavesNoPartOfItBehind() = runTest {
        photoStorage.prepare("cat.jpg").writeBytes(Random(7).nextBytes(256 * 1024))
        val whole = File(temporaryFolder.root, "whole.zip")
        val written = ZipBackupWriter(context, photoStorage, StubDeviceId(), FixedClock(Epoch), "1.0")
            .write(whole.path, BackupContents(encounters = listOf(photoCat("cat.jpg"))))
        assertTrue(written)
        photoStorage.fileFor("cat.jpg").delete()
        val cut = File(target()).apply { writeBytes(whole.readBytes().copyOf((whole.length() * 3 / 4).toInt())) }

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader().read(cut.path))
        assertFalse(photoRestored("cat.jpg"), "a cut-off archive left a partial photo")
    }

    @Test
    fun aRowWhosePhotoPathClimbsOutOfThePhotoDirectoryRefusesTheArchive() = runTest {
        val path = target()
        File(path).writeArchive(MANIFEST_ENTRY to VALID_MANIFEST, ENCOUNTERS_ENTRY to catWithPhotos("../cats_radar.db"))

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader().read(path))
    }

    @Test
    fun aRowWhoseThumbnailPathClimbsOutOfThePhotoDirectoryRefusesTheArchive() = runTest {
        val path = target()
        File(path).writeArchive(
            MANIFEST_ENTRY to VALID_MANIFEST,
            ENCOUNTERS_ENTRY to catWithPhotos("cat.jpg", thumbPath = "../../shared_prefs/settings.xml"),
        )

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader().read(path))
    }
}
