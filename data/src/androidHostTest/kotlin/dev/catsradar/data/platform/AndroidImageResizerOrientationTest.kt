package dev.catsradar.data.platform

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.platform.StoredPhoto
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidImageResizerOrientationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)
    private val resizer = AndroidImageResizer(context, photoStorage)

    private data class Shown(val width: Int, val height: Int, val quadrants: List<Quadrant>)

    private suspend fun storeOriented(orientation: Int): StoredPhoto {
        val source = PhotoFixtures.copyTo(temporaryFolder.root, PhotoFixtures.oriented(orientation))
        return assertNotNull(resizer.store(source.path, "cat-$orientation"))
    }

    private fun shownAs(relativePath: String): Shown {
        val bitmap = checkNotNull(BitmapFactory.decodeFile(photoStorage.resolve(relativePath))) {
            "stored $relativePath does not decode"
        }
        val quadrants = listOf(1 to 1, 3 to 1, 1 to 3, 3 to 3).map { (column, row) ->
            bitmap.getPixel(column * bitmap.width / 4, row * bitmap.height / 4).nearestQuadrant()
        }
        return Shown(bitmap.width, bitmap.height, quadrants)
    }

    private fun Int.nearestQuadrant() = Quadrant.entries.minBy { quadrant ->
        listOf(
            Color.red(this) - quadrant.red,
            Color.green(this) - quadrant.green,
            Color.blue(this) - quadrant.blue,
        ).sumOf { it * it }
    }

    @Test
    fun theCopyIsUprightWhateverOrientationTheExifGives() = runTest {
        val shown = allOrientations.associateWith { shownAs(storeOriented(it).photoPath) }

        assertEquals(allOrientations.associateWith { Shown(300, 400, Quadrant.entries) }, shown)
    }

    @Test
    fun theThumbnailIsUprightWhateverOrientationTheExifGives() = runTest {
        val shown = allOrientations.associateWith { shownAs(assertNotNull(storeOriented(it).thumbPath)) }

        val thumbnail = Shown(Tuning.THUMB_SIZE * 3 / 4, Tuning.THUMB_SIZE, Quadrant.entries)
        assertEquals(allOrientations.associateWith { thumbnail }, shown)
    }

    @Test
    fun anUprightedCopyAsksNoViewerToTurnItAgain() = runTest {
        val stored = storeOriented(ExifInterface.ORIENTATION_ROTATE_90)

        val copy = ExifInterface(photoStorage.resolve(stored.photoPath))
        assertEquals(0 to false, copy.rotationDegrees to copy.isFlipped)
    }

    private companion object {
        val allOrientations = ExifInterface.ORIENTATION_NORMAL..ExifInterface.ORIENTATION_ROTATE_270
    }
}
