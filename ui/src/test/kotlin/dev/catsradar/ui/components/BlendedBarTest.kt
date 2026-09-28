package dev.catsradar.ui.components

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.catsradar.ui.coat.Black
import dev.catsradar.ui.coat.Ginger
import dev.catsradar.ui.coat.White
import dev.catsradar.ui.theme.CatsRadarDarkColors
import dev.catsradar.ui.theme.CatsRadarLightColors
import dev.catsradar.ui.theme.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlendedBarTest {

    private val lightTrack = CatsRadarLightColors.surfaceContainerHighest
    private val lightInk = CatsRadarLightColors.onSurface
    private val darkTrack = CatsRadarDarkColors.surfaceContainerHighest
    private val darkInk = CatsRadarDarkColors.onSurface

    @Test
    fun `a colour already clear of the track is left as it is`() {
        assertEquals(Ginger, Ginger.legibleOn(lightTrack, lightInk, MinContrast))
        assertEquals(White, White.legibleOn(darkTrack, darkInk, MinContrast))
    }

    @Test
    fun `white on the light track is blended toward the text colour just until it stands apart`() {
        val shaded = White.legibleOn(lightTrack, lightInk, MinContrast)

        assertNotEquals(White, shaded)
        assertTrue(shaded.luminance() < White.luminance())
        assertInRange(contrast(shaded, lightTrack))
    }

    @Test
    fun `a faint colour is shaded along the way to the text colour, not toward any other`() {
        val shaded = White.legibleOn(lightTrack, lightInk, MinContrast)

        assertTrue((1..100).any { step -> lerp(White, lightInk, step / 100f) == shaded })
    }

    @Test
    fun `black on the dark track is blended toward the light text colour just until it stands apart`() {
        val shaded = Black.legibleOn(darkTrack, darkInk, MinContrast)

        assertNotEquals(Black, shaded)
        assertTrue(shaded.luminance() > Black.luminance())
        assertInRange(contrast(shaded, darkTrack))
    }

    @Test
    fun `a mixed coat's colours run from the faintest on the track to the clearest`() {
        val gingerAndWhite = listOf(BarPart(Ginger, 2f), BarPart(White, 1f))

        val light = blendStops(gingerAndWhite, lightTrack, lightInk, MinContrast)
        assertEquals(White.legibleOn(lightTrack, lightInk, MinContrast), light.first().second)
        assertEquals(Ginger, light.last().second)

        val dark = blendStops(gingerAndWhite, darkTrack, darkInk, MinContrast)
        assertEquals(Ginger, dark.first().second)
        assertEquals(White, dark.last().second)
    }

    @Test
    fun `each colour holds the middle of its share and melts into the next over its outer part`() {
        val stops = blendStops(listOf(BarPart(Ginger, 2f), BarPart(White, 1f)), darkTrack, darkInk, MinContrast)

        assertEquals(listOf(Ginger, Ginger, White, White), stops.map { it.second })
        val offsets = stops.map { it.first }
        listOf(0f, 2f / 3 - 2f / 3 * 0.3f, 2f / 3 + 1f / 3 * 0.3f, 1f).zip(offsets).forEach { (expected, actual) ->
            assertEquals(expected, actual, 1e-4f)
        }
    }

    @Test
    fun `a one-colour bar is that colour end to end`() {
        assertEquals(
            listOf(0f to Ginger, 1f to Ginger),
            blendStops(listOf(BarPart(Ginger, 1f)), lightTrack, lightInk, MinContrast),
        )
    }

    private fun assertInRange(contrast: Float) {
        assertTrue("contrast $contrast is below $MinContrast", contrast >= MinContrast)
        assertTrue(
            "contrast $contrast went past the first blend that reaches $MinContrast",
            contrast < MinContrast + 0.04f,
        )
    }

    private companion object {
        const val MinContrast = 1.3f
    }
}
