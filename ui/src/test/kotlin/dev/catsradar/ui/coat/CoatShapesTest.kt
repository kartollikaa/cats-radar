package dev.catsradar.ui.coat

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import dev.catsradar.presentation.coat.CoatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class CoatShapesTest {

    private val everyCell = CoatOption.entries + listOf(null)

    @Test
    fun everyCoatHasAShapeOfItsOwnAndSoDoesNoCoat() {
        assertEquals(everyCell.size, everyCell.map(::coatBaseShapeFor).toSet().size)
    }

    @Test
    fun noCoatTakesTheCountsCookieOrASpikyShape() {
        val refused = listOf(
            MaterialShapes.Cookie12Sided, MaterialShapes.Sunny, MaterialShapes.VerySunny, MaterialShapes.Flower,
            MaterialShapes.Burst, MaterialShapes.SoftBurst, MaterialShapes.Boom, MaterialShapes.SoftBoom,
            MaterialShapes.Puffy, MaterialShapes.Cookie6Sided, MaterialShapes.Cookie7Sided, MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover8Leaf, MaterialShapes.Triangle, MaterialShapes.Arrow, MaterialShapes.Diamond,
            MaterialShapes.Heart,
        )
        assertFalse(everyCell.any { cell -> refused.any { it === coatBaseShapeFor(cell) } })
    }

    @Test
    fun noCoatTakesAShapeThatFallsWellShortOfItsSquare() {
        val short = everyCell.filter { cell ->
            val bounds = coatBaseShapeFor(cell).calculateBounds()
            bounds[2] - bounds[0] < ShapeSpan || bounds[3] - bounds[1] < ShapeSpan
        }
        assertTrue("$short fall short of their square", short.isEmpty())
    }

    @Test
    fun everyTileDrawsItsShapeFillingTheSameSquare() {
        everyCell.forEach { cell ->
            val bounds = coatShapeFor(cell).calculateBounds()
            assertEquals("$cell bounds", listOf(0f, 0f, 1f, 1f), bounds.map { Math.round(it / Tolerance) * Tolerance })
        }
    }

    @Test
    fun thePixelSamplesSitOnARoundAndASquareShape() {
        assertSame(MaterialShapes.Circle, coatBaseShapeFor(CoatOption.GINGER))
        assertSame(MaterialShapes.Square, coatBaseShapeFor(CoatOption.GINGER_WHITE))
    }

    private companion object {
        // A shape spanning less of its square than this looks smaller than its neighbours even stretched onto it.
        const val ShapeSpan = 0.9f
        const val Tolerance = 0.002f
    }
}
