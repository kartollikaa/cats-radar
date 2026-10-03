package dev.catsradar.ui.coat

import android.graphics.BitmapFactory
import android.graphics.Color
import dev.catsradar.presentation.coat.CoatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatFaceResourceTest {
    @Test
    fun everyCoatHasDistinctRasterArtworkWithTransparentSpaceAroundItsFace() {
        assertEquals(CoatOption.entries.size, CoatOption.entries.map { it.faceResource() }.toSet().size)
        val fingerprints = CoatOption.entries.map { coat ->
            val name = "cat_face_${coat.name.lowercase(Locale.ROOT)}"
            val file = File("src/main/res/drawable-nodpi/$name.webp")
            assertTrue("missing $name", file.isFile)
            val bitmap = BitmapFactory.decodeFile(file.path)
            assertEquals("$coat width", 512, bitmap.width)
            assertEquals("$coat height", 512, bitmap.height)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val opaque = pixels.count { Color.alpha(it) > 240 }
            assertTrue("$coat has no substantial face", opaque > pixels.size / 4)
            assertTrue("$coat has no transparent background", opaque < pixels.size * 3 / 4)
            for (i in 0 until 512) {
                assertTrue("$coat top", Color.alpha(bitmap.getPixel(i, 0)) < 8)
                assertTrue("$coat bottom", Color.alpha(bitmap.getPixel(i, 511)) < 8)
                assertTrue("$coat left", Color.alpha(bitmap.getPixel(0, i)) < 8)
                assertTrue("$coat right", Color.alpha(bitmap.getPixel(511, i)) < 8)
            }
            pixels.contentHashCode().also { bitmap.recycle() }
        }
        assertEquals("coats share the same artwork", CoatOption.entries.size, fingerprints.toSet().size)
    }
}
