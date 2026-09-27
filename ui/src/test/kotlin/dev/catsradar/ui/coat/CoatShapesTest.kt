package dev.catsradar.ui.coat

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import dev.catsradar.presentation.coat.CoatOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class CoatShapesTest {

    private val everyCell = CoatOption.entries + listOf(null)

    @Test
    fun everyCoatHasAShapeOfItsOwnAndSoDoesNoCoat() {
        assertEquals(everyCell.size, everyCell.map(::coatShapeFor).toSet().size)
    }

    @Test
    fun noCoatTakesTheCountsCookie() {
        assertFalse(everyCell.any { coatShapeFor(it) === MaterialShapes.Cookie12Sided })
    }

    @Test
    fun theCoatsPixelSamplesSitOnARoundAndASquareShape() {
        assertSame(MaterialShapes.Circle, coatShapeFor(CoatOption.GINGER))
        assertSame(MaterialShapes.Square, coatShapeFor(CoatOption.GINGER_WHITE))
    }
}
