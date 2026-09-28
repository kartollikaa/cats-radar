package dev.catsradar.ui.coat

import androidx.compose.ui.graphics.Color
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.components.BarPart
import org.junit.Assert.assertEquals
import org.junit.Test

class CoatBarPartsTest {

    @Test
    fun `a one-colour coat's bar is its fur alone`() {
        assertEquals(listOf(BarPart(White, 1f)), CoatOption.WHITE.barParts())
        assertEquals(listOf(BarPart(Black, 1f)), CoatOption.BLACK.barParts())
    }

    @Test
    fun `a two-colour coat's bar is two thirds its main colour and a third white`() {
        listOf(
            CoatOption.GINGER_WHITE to Ginger,
            CoatOption.BROWN_WHITE to Brown,
            CoatOption.GREY_WHITE to Grey,
            CoatOption.BLACK_WHITE to Black,
        ).forEach { (coat, main) ->
            assertEquals(listOf(main to 2f / 3, White to 1f / 3), coat.barParts().shares())
        }
    }

    @Test
    fun `a mostly-white calico's bar is half white, a little-white one's a fifth`() {
        assertEquals(
            listOf(White to 0.5f, Ginger to 0.25f, Black to 0.25f),
            CoatOption.TRICOLOR_MOSTLY_WHITE.barParts().shares(),
        )
        assertEquals(
            listOf(Ginger to 0.5f, Black to 0.3f, White to 0.2f),
            CoatOption.TRICOLOR_LITTLE_WHITE.barParts().shares(),
        )
    }

    private fun List<BarPart>.shares(): List<Pair<Color, Float>> {
        val total = sumOf { it.weight.toDouble() }.toFloat()
        return map { it.color to it.weight / total }
    }
}
