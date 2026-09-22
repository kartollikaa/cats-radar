package dev.catsradar.data.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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
}
