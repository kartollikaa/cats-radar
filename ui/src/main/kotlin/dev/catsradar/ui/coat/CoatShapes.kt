package dev.catsradar.ui.coat

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.TransformResult
import androidx.graphics.shapes.rectangle
import dev.catsradar.presentation.coat.CoatOption

/** Each coat's own rounded shape, and one for "no coat" ([coat] null), so a coat is known by its shape too. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun coatBaseShapeFor(coat: CoatOption?): RoundedPolygon = when (coat) {
    CoatOption.GINGER -> MaterialShapes.Circle
    CoatOption.GINGER_WHITE -> MaterialShapes.Square
    CoatOption.WHITE -> MaterialShapes.Clover4Leaf
    CoatOption.TRICOLOR_MOSTLY_WHITE -> MaterialShapes.Arch
    CoatOption.TRICOLOR_LITTLE_WHITE -> MaterialShapes.Cookie4Sided
    CoatOption.BROWN -> MaterialShapes.Slanted
    CoatOption.BROWN_WHITE -> MaterialShapes.Gem
    CoatOption.GREY -> MaterialShapes.Fan
    CoatOption.GREY_WHITE -> MaterialShapes.Pentagon
    CoatOption.BLACK -> Leaf
    CoatOption.BLACK_WHITE -> MaterialShapes.Bun
    null -> MaterialShapes.Ghostish
}

// A wider rounding brings the round corners onto the face's ears.
private val Leaf = RoundedPolygon.rectangle(
    width = 1f,
    height = 1f,
    perVertexRounding = listOf(CornerRounding(0.6f), CornerRounding(0.2f), CornerRounding(0.6f), CornerRounding(0.2f)),
)

// Material's shapes stop short of their square by different amounts; stretched onto it, every tile's shape is one size.
private val FilledCoatShapes = (CoatOption.entries + null).associateWith { coatBaseShapeFor(it).filledToSquare() }

/** [coatBaseShapeFor] stretched to fill the unit square, as the tiles draw it. */
internal fun coatShapeFor(coat: CoatOption?): RoundedPolygon = FilledCoatShapes.getValue(coat)

private fun RoundedPolygon.filledToSquare(): RoundedPolygon {
    val bounds = calculateBounds()
    val left = bounds[0]
    val top = bounds[1]
    val width = bounds[2] - left
    val height = bounds[3] - top
    return transformed { x, y -> TransformResult((x - left) / width, (y - top) / height) }
}
