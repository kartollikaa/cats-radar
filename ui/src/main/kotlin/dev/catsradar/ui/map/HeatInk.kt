package dev.catsradar.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.catsradar.ui.theme.contrast

// Blue rather than grey: a grey spot would read as a grey or black coat.
private val UnnotedHeatColour = Color(0xFF3D7BE0)
private const val UnnotedHeatScale = 0.6f

// Below this contrast with the ground a fur's heat melts into the map, so it gets an edge.
private const val MELT_CONTRAST = 1.5f

/** One layer of the heat: the weight it reads, the colour it paints, and the edge it needs, if any. */
internal data class HeatInk(val key: String, val colour: Color, val edge: Color?, val scale: Float = 1f)

/**
 * The heat's layers from the bottom up, on a map whose land is [ground]. Lighter furs lie over darker
 * ones, so where coats mix the spot leans bright rather than muddy.
 */
internal fun heatInks(ground: Color, edge: Color): List<HeatInk> {
    fun edgeFor(colour: Color) = edge.takeIf { contrast(colour, ground) < MELT_CONTRAST }
    val unnoted = HeatInk(UNNOTED_HEAT, UnnotedHeatColour, edgeFor(UnnotedHeatColour), UnnotedHeatScale)
    return listOf(unnoted) + CoatHeatColours.sortedBy { it.luminance() }.map { HeatInk(heatKey(it), it, edgeFor(it)) }
}
