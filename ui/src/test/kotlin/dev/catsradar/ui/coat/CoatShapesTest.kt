package dev.catsradar.ui.coat

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class CoatShapesTest {

    @Test
    fun theShapesGoByColumn() {
        assertSame(MaterialShapes.Circle, coatShapeFor(0))
        assertSame(MaterialShapes.Square, coatShapeFor(1))
        assertSame(MaterialShapes.Clover4Leaf, coatShapeFor(2))
        assertSame(MaterialShapes.Arch, coatShapeFor(3))
    }

    @Test
    fun theShapesRepeatSoTheTwelfthCellIsAnArch() {
        assertSame(MaterialShapes.Circle, coatShapeFor(4))
        assertSame(MaterialShapes.Arch, coatShapeFor(11))
    }
}
