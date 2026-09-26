package dev.catsradar.ui.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoTileTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tile = PhotoTile(size = 120, corner = 30f, rim = 6f, rimColor = Rim)

    @Test
    fun aThumbnailBecomesARoundedTileOfItsMiddleSquareInsideARim() = runBlocking {
        val thumbnail = folder.newFile("cat_thumb.png").also { writeWideCat(it) }

        val drawn = checkNotNull(tile.draw(thumbnail.path, Dispatchers.Unconfined)).asAndroidBitmap()

        assertEquals(120 to 120, drawn.width to drawn.height)
        assertEquals(0, drawn.getPixel(0, 0) ushr ALPHA_SHIFT)
        assertEquals(Cat.toArgb(), drawn.getPixel(60, 60))
        assertEquals(Cat.toArgb(), drawn.getPixel(12, 60))
        listOf(60 to 3, 60 to 116, 3 to 60, 116 to 60).forEach { (x, y) ->
            assertEquals("rim at $x,$y", Rim.toArgb(), drawn.getPixel(x, y))
        }
    }

    @Test
    fun aMissingThumbnailAndAFileThatIsNoImageGiveNoTile() = runBlocking {
        val notAnImage = folder.newFile("notes_thumb.jpg").also { it.writeText("not a photo") }

        assertNull(tile.draw(File(folder.root, "gone_thumb.jpg").path, Dispatchers.Unconfined))
        assertNull(tile.draw(notAnImage.path, Dispatchers.Unconfined))
    }

    // Wider than tall, with bands at the sides that a centred square crop leaves out.
    private fun writeWideCat(file: File) {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Cat.toArgb())
        for (x in (0 until 50) + (250 until 300)) {
            for (y in 0 until 200) bitmap.setPixel(x, y, Side.toArgb())
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val ALPHA_SHIFT = 24
        val Cat = Color(0xFFE0703A)
        val Side = Color(0xFF2E8B57)
        val Rim = Color(0xFF3366CC)
    }
}
