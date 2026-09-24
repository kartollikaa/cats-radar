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
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NATIVE graphics decodes and encodes real pixels. Under Robolectric's default (LEGACY) shadow,
 * BitmapFactory hands back a 100x100 stub whatever the file contains and compress() writes a
 * descriptor string, so every assertion below would hold no matter what the resizer did.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidImageResizerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photoStorage = AndroidPhotoStorage(context)
    private val resizer = AndroidImageResizer(context, photoStorage)

    private fun fixture(name: String) = PhotoFixtures.copyTo(temporaryFolder.root, name).path

    private fun sizeOf(relativePath: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photoStorage.resolve(relativePath), options)
        return options.outWidth to options.outHeight
    }

    @Test
    fun theDecoderUnderTestReturnsTheFixturesRealSize() = runTest {
        // Positive control for every other test here: prove the graphics stack is not a stub
        // before trusting any size it reports. 100x100 means LEGACY graphics crept back in.
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(fixture(PhotoFixtures.LANDSCAPE_WITH_GPS), options)

        assertEquals(3000 to 2250, options.outWidth to options.outHeight)
    }

    @Test
    fun aLandscapePhotoIsCappedOnItsLongestSideKeepingTheRatio() = runTest {
        val stored = assertNotNull(resizer.store(fixture(PhotoFixtures.LANDSCAPE_WITH_GPS), "cat-1"))

        assertEquals(Tuning.PHOTO_MAX_SIDE to 1536, sizeOf(stored.photoPath))
    }

    @Test
    fun aPortraitPhotoIsCappedOnItsHeightNotItsWidth() = runTest {
        val stored = assertNotNull(resizer.store(fixture(PhotoFixtures.PORTRAIT_NO_GPS), "cat-2"))

        val (width, height) = sizeOf(stored.photoPath)
        assertTrue(height <= Tuning.PHOTO_MAX_SIDE && width < height, "was ${width}x$height")
    }

    @Test
    fun aPhotoSmallerThanTheCapIsNotEnlarged() = runTest {
        val stored = assertNotNull(resizer.store(fixture(PhotoFixtures.SMALL_NO_EXIF), "cat-3"))

        assertEquals(800 to 600, sizeOf(stored.photoPath))
    }

    @Test
    fun theThumbnailIsCappedAtTheThumbnailSize() = runTest {
        val landscape = assertNotNull(resizer.store(fixture(PhotoFixtures.LANDSCAPE_WITH_GPS), "cat-4"))
        val portrait = assertNotNull(resizer.store(fixture(PhotoFixtures.PORTRAIT_NO_GPS), "cat-5"))

        val (landscapeWidth, landscapeHeight) = sizeOf(assertNotNull(landscape.thumbPath))
        val (portraitWidth, portraitHeight) = sizeOf(assertNotNull(portrait.thumbPath))
        assertEquals(Tuning.THUMB_SIZE, maxOf(landscapeWidth, landscapeHeight))
        assertEquals(Tuning.THUMB_SIZE, maxOf(portraitWidth, portraitHeight))
    }

    @Test
    fun theStoredCopyCarriesNoneOfTheOriginalsMetadata() = runTest {
        val stored = assertNotNull(resizer.store(fixture(PhotoFixtures.LANDSCAPE_WITH_GPS), "cat-6"))

        val exif = AndroidExifReader(context).read(photoStorage.resolve(stored.photoPath))
        assertNull(exif.lat)
        assertNull(exif.lon)
    }

    @Test
    fun bothCopiesLandInsideTheAppsOwnPhotoDirectory() = runTest {
        val stored = assertNotNull(resizer.store(fixture(PhotoFixtures.SMALL_NO_EXIF), "cat-7"))

        val photosRoot = File(context.filesDir, "photos").canonicalPath
        assertTrue(File(photoStorage.resolve(stored.photoPath)).canonicalPath.startsWith(photosRoot))
        assertTrue(stored.photoPath.first() != '/', "stored path must be relative: ${stored.photoPath}")
    }

    @Test
    fun anUndecodableSourceStoresNothingAndReportsIt() = runTest {
        assertNull(resizer.store(fixture(PhotoFixtures.TRUNCATED), "cat-8"))
        assertNull(resizer.store("${temporaryFolder.root}/nothing-here.jpg", "cat-9"))
    }
}
