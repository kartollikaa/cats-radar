package dev.catsradar.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.catsradar.ui.testing.MIN_TEXT_CONTRAST
import dev.catsradar.ui.testing.contrast
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.sqrt

private const val MIN_TEAL_CHROMA = 0.25f

private val TextOnSurface: List<Triple<String, (ColorScheme) -> Color, (ColorScheme) -> Color>> = listOf(
    Triple("onPrimary on primary", { it.onPrimary }, { it.primary }),
    Triple("onPrimaryContainer on primaryContainer", { it.onPrimaryContainer }, { it.primaryContainer }),
    Triple("onSecondary on secondary", { it.onSecondary }, { it.secondary }),
    Triple("onSecondaryContainer on secondaryContainer", { it.onSecondaryContainer }, { it.secondaryContainer }),
    Triple("onTertiary on tertiary", { it.onTertiary }, { it.tertiary }),
    Triple("onTertiaryContainer on tertiaryContainer", { it.onTertiaryContainer }, { it.tertiaryContainer }),
    Triple("onError on error", { it.onError }, { it.error }),
    Triple("onErrorContainer on errorContainer", { it.onErrorContainer }, { it.errorContainer }),
    Triple("onSurface on surface", { it.onSurface }, { it.surface }),
    Triple("onSurfaceVariant on surface", { it.onSurfaceVariant }, { it.surface }),
    Triple("onSurface on surfaceContainerHighest", { it.onSurface }, { it.surfaceContainerHighest }),
    Triple("onSurfaceVariant on surfaceContainerHighest", { it.onSurfaceVariant }, { it.surfaceContainerHighest }),
    Triple("inverseOnSurface on inverseSurface", { it.inverseOnSurface }, { it.inverseSurface }),
    Triple("onPrimaryFixed on primaryFixed", { it.onPrimaryFixed }, { it.primaryFixed }),
    Triple("onSecondaryFixed on secondaryFixed", { it.onSecondaryFixed }, { it.secondaryFixed }),
    Triple("onTertiaryFixed on tertiaryFixed", { it.onTertiaryFixed }, { it.tertiaryFixed }),
    Triple("onSurface on surfaceContainerLow", { it.onSurface }, { it.surfaceContainerLow }),
    Triple("onSurfaceVariant on surfaceContainerLow", { it.onSurfaceVariant }, { it.surfaceContainerLow }),
    // Accent colours drawn as text.
    Triple("secondary on surfaceContainer", { it.secondary }, { it.surfaceContainer }),
    Triple("primary on surface", { it.primary }, { it.surface }),
    Triple("error on surface", { it.error }, { it.surface }),
)

private val Schemes = listOf(
    Triple("light", CatsRadarLightColors, lightColorScheme()),
    Triple("dark", CatsRadarDarkColors, darkColorScheme()),
)

class CatsRadarColorsTest {

    @Test
    fun everyTextColourReadsOnTheSurfaceItIsMeantForInBothThemes() {
        val failures = Schemes.flatMap { (name, scheme, _) ->
            TextOnSurface.mapNotNull { (pair, text, surface) ->
                val ratio = contrast(text(scheme), surface(scheme))
                "$name: $pair is ${"%.2f".format(ratio)}:1".takeIf { ratio < MIN_TEXT_CONTRAST }
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // Every role the scheme has, found by reflection, so a role this file never heard of still counts.
    // Pure white and black are the same in any palette and are allowed to match.
    @Test
    fun noRoleIsLeftAtMaterialsDefaultColour() {
        val leftovers = Schemes.flatMap { (name, scheme, baseline) ->
            val defaults = roles(baseline)
            roles(scheme)
                .filter { (role, colour) -> colour == defaults.getValue(role) }
                .filter { (_, colour) -> colour != Color.White && colour != Color.Black }
                .map { (role, _) -> "$name.$role" }
        }

        assertTrue("still Material's default: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun thePrimaryIsTheIconsTeal() {
        listOf(CatsRadarLightColors.primary, CatsRadarDarkColors.primary).forEach { primary ->
            val hue = hueDegrees(primary)
            assertTrue("hue $hue is not teal", hue in 150.0..185.0)
            assertTrue("$primary is too grey to be teal", chroma(primary) >= MIN_TEAL_CHROMA)
        }
    }

    private fun roles(scheme: ColorScheme): Map<String, Color> =
        ColorScheme::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
            .filter { it.name.startsWith("get") }
            .associate { it.name.substringBefore('-') to Color((it.invoke(scheme) as Long).toULong()) }

    private fun chroma(color: Color): Float =
        maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)

    private fun hueDegrees(color: Color): Double {
        val alpha = color.red - (color.green + color.blue) / 2
        val beta = sqrt(3.0).toFloat() / 2 * (color.green - color.blue)
        return (Math.toDegrees(atan2(beta.toDouble(), alpha.toDouble())) + 360) % 360
    }
}
