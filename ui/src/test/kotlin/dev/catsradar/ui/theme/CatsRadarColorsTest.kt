package dev.catsradar.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.sqrt

// WCAG AA for body text.
private const val MIN_TEXT_CONTRAST = 4.5f

private val TextOnSurface: List<Triple<String, (ColorScheme) -> Color, (ColorScheme) -> Color>> = listOf(
    Triple("onPrimary on primary", { it.onPrimary }, { it.primary }),
    Triple("onPrimaryContainer on primaryContainer", { it.onPrimaryContainer }, { it.primaryContainer }),
    Triple("onSecondaryContainer on secondaryContainer", { it.onSecondaryContainer }, { it.secondaryContainer }),
    Triple("onTertiary on tertiary", { it.onTertiary }, { it.tertiary }),
    Triple("onTertiaryContainer on tertiaryContainer", { it.onTertiaryContainer }, { it.tertiaryContainer }),
    Triple("onError on error", { it.onError }, { it.error }),
    Triple("onErrorContainer on errorContainer", { it.onErrorContainer }, { it.errorContainer }),
    Triple("onSurface on surface", { it.onSurface }, { it.surface }),
    Triple("onSurfaceVariant on surface", { it.onSurfaceVariant }, { it.surface }),
    Triple("onSurface on surfaceContainerHighest", { it.onSurface }, { it.surfaceContainerHighest }),
    Triple("onSurfaceVariant on surfaceContainerHighest", { it.onSurfaceVariant }, { it.surfaceContainerHighest }),
)

class CatsRadarColorsTest {

    @Test
    fun everyTextColourReadsOnTheSurfaceItIsMeantForInBothThemes() {
        val schemes = listOf("light" to CatsRadarLightColors, "dark" to CatsRadarDarkColors)
        val failures = schemes.flatMap { (name, scheme) ->
            TextOnSurface.mapNotNull { (pair, text, surface) ->
                val ratio = contrast(text(scheme), surface(scheme))
                "$name: $pair is ${"%.2f".format(ratio)}:1".takeIf { ratio < MIN_TEXT_CONTRAST }
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun thePrimaryIsTheIconsTealNotMaterialsDefaultPurple() {
        assertNotEquals(lightColorScheme().primary, CatsRadarLightColors.primary)
        listOf(CatsRadarLightColors.primary, CatsRadarDarkColors.primary).forEach { primary ->
            val hue = hueDegrees(primary)
            assertTrue("hue $hue is not teal", hue in 150.0..185.0)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    // The hue of the colour in the sRGB-derived opponent plane; coarse, but it only has to tell teal
    // from purple.
    private fun hueDegrees(color: Color): Double {
        val alpha = color.red - (color.green + color.blue) / 2
        val beta = sqrt(3.0).toFloat() / 2 * (color.green - color.blue)
        return (Math.toDegrees(atan2(beta.toDouble(), alpha.toDouble())) + 360) % 360
    }
}
