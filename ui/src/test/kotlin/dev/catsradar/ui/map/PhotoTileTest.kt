package dev.catsradar.ui.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoTileTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tile = PhotoTile(size = 120, corner = 30f, rim = 6f, rimColor = Rim)
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var imageLoader: ImageLoader

    @Before
    fun setUp() {
        imageLoader = ImageLoader.Builder(context).build()
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
    }

    @Test
    fun aLargeThumbnailIsSampledToTheTileFillSizeInSoftware() = runBlocking {
        val thumbnail = folder.newFile("large_thumb.png").also { writeWideCat(it) }
        val loader = RecordingImageLoader(imageLoader)
        val mapTile = tile.copy(size = 44)

        checkNotNull(mapTile.draw(thumbnail.path, context, loader))

        val request = checkNotNull(loader.request)
        assertEquals(Size(44, 44), request.sizeResolver.size())
        assertEquals(Scale.FILL, request.scale)
        assertFalse(request.allowHardware)
        assertEquals(CachePolicy.DISABLED, request.memoryCachePolicy)
        val result = checkNotNull(loader.result)
        assertTrue(result.isSampled)
        assertEquals(66 to 44, result.image.width to result.image.height)
    }

    @Test
    fun aThumbnailBecomesARoundedTileOfItsMiddleSquareInsideARim() = runBlocking {
        val thumbnail = folder.newFile("cat_thumb.png").also { writeWideCat(it) }

        val drawn = checkNotNull(tile.draw(thumbnail.path, context, imageLoader)).asAndroidBitmap()

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

        assertNull(tile.draw(File(folder.root, "gone_thumb.jpg").path, context, imageLoader))
        assertNull(tile.draw(notAnImage.path, context, imageLoader))
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

private class RecordingImageLoader(private val delegate: ImageLoader) : ImageLoader by delegate {
    var request: ImageRequest? = null
    var result: SuccessResult? = null

    override suspend fun execute(request: ImageRequest): ImageResult {
        this.request = request
        return delegate.execute(request).also { result = it as? SuccessResult }
    }
}
