package dev.catsradar.ui.map

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.luminance
import dev.catsradar.ui.coat.Black
import dev.catsradar.ui.coat.Brown
import dev.catsradar.ui.coat.Ginger
import dev.catsradar.ui.coat.Grey
import dev.catsradar.ui.coat.White
import dev.catsradar.ui.coat.faceRim
import dev.catsradar.ui.theme.CatsRadarDarkColors
import dev.catsradar.ui.theme.CatsRadarLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatInkTest {

    @Test
    fun everyCoatsHeatIsItsOwnFurOnBothThemes() {
        listOf(CatsRadarLightColors, CatsRadarDarkColors).forEach { scheme ->
            val coats = inks(scheme).filter { it.key != UNNOTED_HEAT }

            assertEquals(setOf(Ginger, White, Black, Brown, Grey), coats.map { it.colour }.toSet())
            coats.forEach { assertEquals(heatKey(it.colour), it.key) }
        }
    }

    @Test
    fun onlyAFurThatMeltsIntoTheThemeGetsAnEdgeAndTheEdgeIsTheRim() {
        assertEquals(setOf(White), edged(CatsRadarLightColors))
        assertEquals(setOf(Black), edged(CatsRadarDarkColors))
        listOf(CatsRadarLightColors, CatsRadarDarkColors).forEach { scheme ->
            inks(scheme).mapNotNull { it.edge }.forEach { assertEquals(scheme.faceRim(), it) }
        }
    }

    @Test
    fun aCatWithNoCoatIsASmallerBlueSpotAndEveryCoatIsFullSize() {
        listOf(CatsRadarLightColors, CatsRadarDarkColors).forEach { scheme ->
            val inks = inks(scheme)
            val unnoted = inks.single { it.key == UNNOTED_HEAT }
            val blue = unnoted.colour

            assertTrue("$blue is not blue", blue.blue > maxOf(blue.red, blue.green))
            assertTrue(unnoted.scale < 1f)
            assertTrue(inks.filter { it.key != UNNOTED_HEAT }.all { it.scale == 1f })
        }
    }

    @Test
    fun theNoCoatHeatLiesLowestAndLighterFursLieOverDarkerOnes() {
        listOf(CatsRadarLightColors, CatsRadarDarkColors).forEach { scheme ->
            val inks = inks(scheme)
            val furs = inks.drop(1).map { it.colour.luminance() }

            assertEquals(UNNOTED_HEAT, inks.first().key)
            assertEquals(furs.sorted(), furs)
        }
    }

    private fun inks(scheme: ColorScheme) = heatInks(ground = scheme.surface, edge = scheme.faceRim())

    private fun edged(scheme: ColorScheme) = inks(scheme).filter { it.edge != null }.map { it.colour }.toSet()
}
