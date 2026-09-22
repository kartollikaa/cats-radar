package dev.catsradar.domain.photo

import dev.catsradar.domain.Tuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScaledSizeTest {

    @Test
    fun `a landscape photo is capped on its width, the side that is actually longest`() {
        assertEquals(ScaledSize(2048, 1536), scaleToFit(4000, 3000, 2048))
    }

    @Test
    fun `a portrait photo is capped on its height, not on its width`() {
        assertEquals(ScaledSize(1536, 2048), scaleToFit(3000, 4000, 2048))
    }

    @Test
    fun `a photo already within the cap is left exactly as it is`() {
        assertEquals(ScaledSize(800, 600), scaleToFit(800, 600, 2048))
        assertEquals(ScaledSize(2048, 100), scaleToFit(2048, 100, 2048))
    }

    @Test
    fun `the aspect ratio survives scaling to within a rounded pixel`() {
        val source = 4032 to 3024
        val scaled = scaleToFit(source.first, source.second, Tuning.PHOTO_MAX_SIDE)

        val sourceRatio = source.first.toDouble() / source.second
        val scaledRatio = scaled.width.toDouble() / scaled.height
        assertTrue(
            kotlin.math.abs(sourceRatio - scaledRatio) < 0.01,
            "ratio drifted: $sourceRatio -> $scaledRatio ($scaled)",
        )
    }

    @Test
    fun `an extreme panorama keeps at least one pixel on its short side`() {
        val scaled = scaleToFit(20_000, 3, 2048)

        assertEquals(2048, scaled.width)
        assertEquals(1, scaled.height)
    }

    @Test
    fun `a square photo stays square`() {
        assertEquals(ScaledSize(256, 256), scaleToFit(3000, 3000, 256))
    }

    @Test
    fun `a nonsensical size is rejected rather than producing a nonsensical result`() {
        assertFailsWith<IllegalArgumentException> { scaleToFit(0, 100, 2048) }
        assertFailsWith<IllegalArgumentException> { scaleToFit(100, -1, 2048) }
        assertFailsWith<IllegalArgumentException> { scaleToFit(100, 100, 0) }
    }
}
