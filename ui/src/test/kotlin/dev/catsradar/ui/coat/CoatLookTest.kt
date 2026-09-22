package dev.catsradar.ui.coat

import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.testing.MIN_SHAPE_CONTRAST
import dev.catsradar.ui.testing.contrast
import dev.catsradar.ui.theme.CatsRadarDarkColors
import dev.catsradar.ui.theme.CatsRadarLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoatLookTest {

    @Test
    fun everyFaceHasEyesThatShowAgainstItsFur() {
        val invisible = CoatOption.entries.filter { contrast(it.look().eyes, it.look().fur) < MIN_SHAPE_CONTRAST }

        assertTrue("eyes lost in the fur of $invisible", invisible.isEmpty())
    }

    @Test
    fun theLineAroundEveryFaceShowsOnBothThemes() {
        listOf("light" to CatsRadarLightColors, "dark" to CatsRadarDarkColors).forEach { (name, scheme) ->
            val ratio = contrast(scheme.faceRim(), scheme.surface)
            assertTrue("$name rim is $ratio:1 on its surface", ratio >= MIN_SHAPE_CONTRAST)
        }
    }

    @Test
    fun bothCalicoCoatsShowGingerBlackAndWhite() {
        listOf(CoatOption.TRICOLOR_MOSTLY_WHITE, CoatOption.TRICOLOR_LITTLE_WHITE).forEach { coat ->
            assertEquals("$coat", setOf(Ginger, Black, White), coat.look().colours)
        }
    }

    @Test
    fun mostlyWhiteIsWhiteFurAndLittleWhiteIsNot() {
        assertEquals(White, CoatOption.TRICOLOR_MOSTLY_WHITE.look().fur)
        assertTrue(CoatOption.TRICOLOR_LITTLE_WHITE.look().fur != White)
    }

    @Test
    fun everyAndWhiteCoatHasAWhiteMuzzleAndNoSolidCoatHasOne() {
        val andWhite = setOf(
            CoatOption.GINGER_WHITE,
            CoatOption.BROWN_WHITE,
            CoatOption.GREY_WHITE,
            CoatOption.BLACK_WHITE,
        )
        val solid = setOf(CoatOption.GINGER, CoatOption.WHITE, CoatOption.BROWN, CoatOption.GREY, CoatOption.BLACK)

        andWhite.forEach { assertEquals("$it", White, it.look().muzzle) }
        solid.forEach { assertEquals("$it", setOf(it.look().fur), it.look().colours) }
    }

    @Test
    fun noTwoCoatsLookTheSame() {
        val looks = CoatOption.entries.groupBy { it.look() }.filterValues { it.size > 1 }

        assertTrue("coats that collapse onto one face: ${looks.values}", looks.isEmpty())
    }
}
