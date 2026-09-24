package dev.catsradar.data.platform

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.domain.platform.StoredPhoto
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidImageResizerNamingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resizer = AndroidImageResizer(context, AndroidPhotoStorage(context))

    @Test
    fun storingAgainForAnEncounterWritesTheSameNamesItsRowHolds() = runTest {
        val source = PhotoFixtures.copyTo(temporaryFolder.root, PhotoFixtures.SMALL_NO_EXIF).path

        val first = resizer.store(source, "cat-1")
        val again = resizer.store(source, "cat-1")

        val named = StoredPhoto(photoPath = "cat-1.jpg", thumbPath = "cat-1_thumb.jpg")
        assertEquals(named to named, first to again)
    }
}
