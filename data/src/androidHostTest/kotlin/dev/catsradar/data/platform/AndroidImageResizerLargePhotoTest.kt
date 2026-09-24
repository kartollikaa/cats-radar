package dev.catsradar.data.platform

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.Tuning
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidImageResizerLargePhotoTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)
    private val resizer = AndroidImageResizer(context, photoStorage)

    private fun largePhoto() = PhotoFixtures.copyTo(temporaryFolder.root, PhotoFixtures.LARGE_ROTATED).path

    private fun sizeOf(relativePath: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photoStorage.resolve(relativePath), options)
        return options.outWidth to options.outHeight
    }

    @Test
    fun aPhotoOverTwiceTheCapIsDecodedShrunkButNeverBelowTheCap() {
        val decoded = assertNotNull(context.decodeShrunk(largePhoto(), Tuning.PHOTO_MAX_SIDE))

        val longestSide = maxOf(decoded.bitmap.width, decoded.bitmap.height)
        assertTrue(longestSide in Tuning.PHOTO_MAX_SIDE..2 * Tuning.PHOTO_MAX_SIDE, "decoded at $longestSide")
        assertEquals(4099 to 3082, decoded.fileWidth to decoded.fileHeight)
    }

    @Test
    fun aPhotoDecodedShrunkIsStillSizedFromTheOriginal() = runTest {
        val stored = assertNotNull(resizer.store(largePhoto(), "cat-large"))

        assertEquals(1540 to Tuning.PHOTO_MAX_SIDE, sizeOf(stored.photoPath))
        assertEquals(192 to Tuning.THUMB_SIZE, sizeOf(assertNotNull(stored.thumbPath)))
    }
}
