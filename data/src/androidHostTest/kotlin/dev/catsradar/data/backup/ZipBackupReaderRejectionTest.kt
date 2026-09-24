package dev.catsradar.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.data.platform.AndroidPhotoStorage
import dev.catsradar.domain.platform.BackupReadResult
import dev.catsradar.domain.platform.BackupRejection
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Every way an archive can be refused, and the one thing that still reads without it. */
@RunWith(AndroidJUnit4::class)
class ZipBackupReaderRejectionTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val reader get() = ZipBackupReader(context, AndroidPhotoStorage(context))

    private val target: String get() = File(temporaryFolder.root, "backup.zip").path

    @Test
    fun anArchiveFromANewerVersionIsRefusedWholesale() = runTest {
        val path = target
        File(path).writeArchive(
            MANIFEST_ENTRY to """{"formatVersion":99,"exportedAt":0,"deviceId":"d","appVersion":"9"}""",
            ENCOUNTERS_ENTRY to "[]",
        )

        val read = reader.read(path)

        assertEquals(BackupReadResult.Rejected(BackupRejection.TOO_NEW), read)
    }

    @Test
    fun aFileThatIsNotAnArchiveIsRefused() = runTest {
        val path = File(temporaryFolder.root, "notes.txt").also { it.writeText("not a zip") }.path

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader.read(path))
    }

    @Test
    fun anArchiveWithNoManifestIsRefused() = runTest {
        val path = target
        File(path).writeArchive(ENCOUNTERS_ENTRY to "[]")

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader.read(path))
    }

    @Test
    fun anArchiveWhoseRowsAreCorruptIsRefusedRatherThanPartlyRead() = runTest {
        val path = target
        File(path).writeArchive(
            MANIFEST_ENTRY to VALID_MANIFEST,
            ENCOUNTERS_ENTRY to """[{"id":"a"}]""",
        )

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader.read(path))
    }

    @Test
    fun aPhotoEntryThatClimbsOutOfThePhotoDirectoryIsRefused() = runTest {
        val path = target
        File(path).writeArchive(
            MANIFEST_ENTRY to VALID_MANIFEST,
            ENCOUNTERS_ENTRY to "[]",
            "${PHOTOS_PREFIX}../../../escaped.txt" to "owned",
        )

        // The whole archive is refused rather than the entry skipped: an archive that tried this
        // is not one to take rows from either.
        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader.read(path))
        assertTrue(!File(context.filesDir.parentFile, "escaped.txt").exists())
    }

    @Test
    fun aFormatOneArchiveWithNoWalksStillReads() = runTest {
        val path = target
        File(path).writeArchive(MANIFEST_ENTRY to VALID_MANIFEST, ENCOUNTERS_ENTRY to "[]")

        val read = reader.read(path)

        assertIs<BackupReadResult.Readable>(read)
        assertEquals(emptyList(), read.contents.walks)
        assertEquals(emptyList(), read.contents.trackPoints)
    }

    @Test
    fun aNewerArchiveWhoseRowsThisVersionCannotParseIsRefusedAsNewerWhereverItsManifestIs() = runTest {
        val path = target
        File(path).writeArchive(
            ENCOUNTERS_ENTRY to """[{"id":"a","occurredAt":"2026-09-20T08:00:00Z"}]""",
            MANIFEST_ENTRY to NEWER_MANIFEST,
        )

        assertEquals(BackupReadResult.Rejected(BackupRejection.TOO_NEW), reader.read(path))
    }

    @Test
    fun aNewerArchiveWhoseManifestHasChangedShapeIsStillRefusedAsNewer() = runTest {
        val path = target
        File(path).writeArchive(
            MANIFEST_ENTRY to """{"formatVersion":99,"exportedAt":"2031-01-01T00:00:00Z","device":{"id":"d"}}""",
            ENCOUNTERS_ENTRY to "[]",
        )

        assertEquals(BackupReadResult.Rejected(BackupRejection.TOO_NEW), reader.read(path))
    }

    @Test
    fun aCurrentArchiveCutOffBetweenItsListsIsRefusedRatherThanReadWithoutThem() = runTest {
        val path = target
        File(path).writeArchive(
            MANIFEST_ENTRY to CurrentManifest,
            ENCOUNTERS_ENTRY to "[]",
            PLACE_CELLS_ENTRY to "[]",
        )

        assertEquals(BackupReadResult.Rejected(BackupRejection.UNREADABLE), reader.read(path))
    }

    @Test
    fun anArchiveWithNoPlaceCellsFileStillReads() = runTest {
        val path = target
        File(path).writeArchive(
            MANIFEST_ENTRY to VALID_MANIFEST,
            ENCOUNTERS_ENTRY to "[]",
        )

        val read = reader.read(path)

        assertIs<BackupReadResult.Readable>(read)
        assertEquals(emptyList(), read.contents.placeCells)
    }
}
