package dev.catsradar.ui.coat

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.Cubic
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.encounters.LeadFaceShare
import dev.catsradar.ui.encounters.OutingLeadSize
import dev.catsradar.ui.encounters.SelectionRingWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    @Test
    fun onlyTheKnownCoatsHaveARingOverTheirFace() {
        // These shapes reach their faces' ears; no other coat may join them.
        assertEquals(
            "the coat grid",
            setOf(CoatOption.GREY, CoatOption.BLACK_WHITE),
            coatsUnderTheRing(TileSize, TileFaceSize / TileSize, TileRingWidth),
        )
        assertEquals(
            "an outing card",
            setOf(CoatOption.GREY, CoatOption.BLACK_WHITE),
            coatsUnderTheRing(OutingLeadSize, LeadFaceShare, SelectionRingWidth),
        )
    }

    private fun coatsUnderTheRing(tile: Dp, faceShare: Float, ring: Dp): Set<CoatOption> {
        val clearance = (ring + FaceRimWidth / 2) / tile
        return CoatOption.entries.filter { faceMargin(it, faceShare) < clearance }.toSet()
    }

    private fun faceMargin(coat: CoatOption, faceShare: Float): Float {
        val shape = coatShapeFor(coat)
        val edge = shape.cubics.flatMap { cubic -> (0 until Samples).map { cubic.at(it / Samples.toFloat()) } }
        val inset = (1 - faceShare) / 2
        return headOutline(coat).minOf { (x, y) ->
            edge.signedDistance(inset + x * faceShare, inset + y * faceShare)
        }
    }

    private fun headOutline(coat: CoatOption): List<Pair<Float, Float>> {
        val file = File("src/main/res/drawable-nodpi/cat_face_${coat.name.lowercase(Locale.ROOT)}.webp")
        val bitmap = BitmapFactory.decodeFile(file.path)
        val points = buildList {
            for (y in 0 until bitmap.height step 4) {
                val row = (0 until bitmap.width).filter { Color.alpha(bitmap.getPixel(it, y)) > 127 }
                if (row.isNotEmpty()) {
                    add(row.first().toFloat() / bitmap.width to y.toFloat() / bitmap.height)
                    add(row.last().toFloat() / bitmap.width to y.toFloat() / bitmap.height)
                }
            }
        }
        bitmap.recycle()
        return points
    }

    private fun Cubic.at(t: Float): Pair<Float, Float> =
        bezier(t, anchor0X, control0X, control1X, anchor1X) to bezier(t, anchor0Y, control0Y, control1Y, anchor1Y)

    private fun bezier(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val u = 1 - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }

    // Positive inside the outline, negative outside.
    private fun List<Pair<Float, Float>>.signedDistance(x: Float, y: Float): Float {
        val nearest = minOf { (px, py) -> hypot(px - x, py - y) }
        val crossings = indices.count { i ->
            val (x0, y0) = this[i]
            val (x1, y1) = this[(i + 1) % size]
            (y0 > y) != (y1 > y) && x < x0 + (y - y0) / (y1 - y0) * (x1 - x0)
        }
        return if (crossings % 2 == 1) nearest else -nearest
    }

    private companion object {
        const val Samples = 40

        // A shape spanning less of its square than this looks smaller than its neighbours even stretched onto it.
        const val ShapeSpan = 0.9f
        const val Tolerance = 0.002f
    }
}
