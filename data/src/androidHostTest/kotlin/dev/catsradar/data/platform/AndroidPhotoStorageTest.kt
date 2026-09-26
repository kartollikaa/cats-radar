package dev.catsradar.data.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.platform.StoredPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidPhotoStorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)
    private val photosRoot = File(context.filesDir, "photos")

    @Test
    fun aStoredPathResolvesInsideTheAppsOwnPhotoDirectory() {
        val resolved = File(photoStorage.resolve("cat-1.jpg")).canonicalPath

        assertTrue(resolved.startsWith(photosRoot.canonicalPath + File.separator), resolved)
    }

    @Test
    fun aPathTryingToClimbOutOfThePhotoDirectoryIsRefused() {
        // Stored paths are data. A row carrying "../" — corrupt, imported, or hand-edited — would
        // otherwise reach the database file next door.
        assertFailsWith<IllegalArgumentException> { photoStorage.resolve("../databases/cats.db") }
        assertFailsWith<IllegalArgumentException> { photoStorage.resolve("a/../../secrets") }
    }

    @Test
    fun deletingRemovesTheFileAndDeletingWhatIsNotThereIsNotAnError() = runTest {
        val file = photoStorage.prepare("cat-2.jpg").apply { writeText("x") }
        assertTrue(file.exists())

        photoStorage.delete("cat-2.jpg")
        photoStorage.delete("cat-2.jpg")

        assertFalse(file.exists())
    }

    @Test
    fun copyingMakesIndependentPhotoAndThumbnailFilesUnderTheNewName() = runTest {
        photoStorage.prepare("copy-source.jpg").writeText("photo")
        photoStorage.prepare("copy-source_thumb.jpg").writeText("thumb")

        val copied = photoStorage.copy(
            StoredPhoto("copy-source.jpg", "copy-source_thumb.jpg"),
            "copy-new-photo",
        )

        assertEquals(StoredPhoto("copy-new-photo.jpg", "copy-new-photo_thumb.jpg"), copied)
        assertEquals("photo", photoStorage.fileFor(copied.photoPath).readText())
        assertEquals("thumb", photoStorage.fileFor(copied.thumbPath!!).readText())
        assertTrue(photoStorage.fileFor("copy-source.jpg").exists())
        assertTrue(photoStorage.fileFor("copy-source_thumb.jpg").exists())
    }

    @Test
    fun copyingAPhotoWithoutAThumbnailKeepsTheThumbnailAbsent() = runTest {
        photoStorage.prepare("no-thumb-source.jpg").writeText("photo")

        val copied = photoStorage.copy(StoredPhoto("no-thumb-source.jpg", null), "no-thumb-new-photo")

        assertEquals(StoredPhoto("no-thumb-new-photo.jpg", null), copied)
        assertFalse(photoStorage.fileFor("no-thumb-new-photo_thumb.jpg").exists())
    }

    @Test
    fun aThumbnailCopyFailureRemovesThePhotoCopiedEarlierInTheCall() = runTest {
        photoStorage.prepare("partial-source.jpg").writeText("photo")
        photoStorage.prepare("partial-source_thumb.jpg").writeText("thumb")
        photoStorage.prepare("partial-new-photo_thumb.jpg").mkdirs()

        assertFails {
            photoStorage.copy(
                StoredPhoto("partial-source.jpg", "partial-source_thumb.jpg"),
                "partial-new-photo",
            )
        }

        assertFalse(photoStorage.fileFor("partial-new-photo.jpg").exists())
    }

    @Test
    fun aWriteFailureRemovesThePartiallyWrittenDestination() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val failingStorage = AndroidPhotoStorage(context, dispatcher) { _, destination ->
            destination.writeText("partial")
            throw IOException("write failed")
        }
        failingStorage.prepare("write-failure-source.jpg").writeText("photo")

        assertFailsWith<IOException> {
            failingStorage.copy(StoredPhoto("write-failure-source.jpg", null), "write-failure-new-photo")
        }

        assertFalse(failingStorage.fileFor("write-failure-new-photo.jpg").exists())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun cancellationWhileReturningFromIoRemovesCompletedCopies() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        lateinit var copyJob: Job
        val cancellableStorage = AndroidPhotoStorage(context, dispatcher) { source, destination ->
            source.copyTo(destination)
            if (destination.name.endsWith("_thumb.jpg")) copyJob.cancel()
        }
        cancellableStorage.prepare("cancel-source.jpg").writeText("photo")
        cancellableStorage.prepare("cancel-source_thumb.jpg").writeText("thumb")

        copyJob = launch {
            assertFailsWith<CancellationException> {
                cancellableStorage.copy(
                    StoredPhoto("cancel-source.jpg", "cancel-source_thumb.jpg"),
                    "cancel-new-photo",
                )
            }
        }
        runCurrent()

        assertFalse(cancellableStorage.fileFor("cancel-new-photo.jpg").exists())
        assertFalse(cancellableStorage.fileFor("cancel-new-photo_thumb.jpg").exists())
    }
}
