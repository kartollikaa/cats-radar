package dev.catsradar.ui.coat

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.RoundedPolygon
import dev.catsradar.presentation.coat.CoatOption
import kotlin.math.hypot
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

    @Test
    fun aRingedTileKeepsItsFaceClearOfTheRing() {
        val faceShare = TileFaceSize / TileSize
        val clearance = (TileRingWidth + FaceRimWidth / 2) / TileSize
        // Grey's fan still reaches its face's ear.
        val crowded = (CoatOption.entries - CoatOption.GREY).filter {
            faceMargin(coatShapeFor(it), faceShare) < clearance
        }
        assertTrue("$crowded have the ring over their face", crowded.isEmpty())
    }

    private fun faceMargin(shape: RoundedPolygon, faceShare: Float): Float {
        val edge = shape.cubics.flatMap { cubic -> (0 until Samples).map { cubic.at(it / Samples.toFloat()) } }
        val inset = (1 - faceShare) / 2
        return headOutline().minOf { (x, y) ->
            edge.signedDistance(inset + x / FaceUnits * faceShare, inset + y / FaceUnits * faceShare)
        }
    }

    private fun headOutline(): List<Pair<Float, Float>> {
        var from = 0f to 0f
        return PathParser().parsePathString(CatFacePaths.Head).toNodes().flatMap { node ->
            val points = when (node) {
                is PathNode.MoveTo -> listOf(node.x to node.y)
                is PathNode.LineTo -> (1..Samples).map {
                    val t = it / Samples.toFloat()
                    (from.first + (node.x - from.first) * t) to (from.second + (node.y - from.second) * t)
                }
                is PathNode.CurveTo -> (1..Samples).map {
                    val t = it / Samples.toFloat()
                    val x = bezier(t, from.first, node.x1, node.x2, node.x3)
                    x to bezier(t, from.second, node.y1, node.y2, node.y3)
                }
                else -> emptyList()
            }
            points.lastOrNull()?.let { from = it }
            points
        }
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
