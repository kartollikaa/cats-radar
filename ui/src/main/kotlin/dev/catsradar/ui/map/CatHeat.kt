package dev.catsradar.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.heatmapDensity
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.NumberValue
import org.maplibre.compose.layers.HeatmapLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

private const val HeatLowDensity = 0.15
private const val HeatLowAlpha = 0.8f
private const val HeatOpacity = 0.95f
private const val EdgeDensity = 0.04
private const val EdgeFadeDensity = 0.12
private const val EdgeAlpha = 0.5f
private const val EdgeReach = 1.12f

// Each cat's heat is sized and weighted by zoom: at one size for every zoom, a city's cats merge into one blob.
private fun heatRadius(scale: Float) = interpolate(
    linear(),
    zoom(),
    9 to const(8.dp * scale),
    11 to const(16.dp * scale),
    14 to const(24.dp * scale),
    16 to const(30.dp * scale),
)
private val HeatIntensity = interpolate(linear(), zoom(), 9 to const(0.7f), 15 to const(1f))

@Composable
internal fun CatHeat(cats: FeatureCollection<Point, JsonObject>, colors: CatLayerColors) {
    // Its own source, unclustered: over a clustered one, a cluster of ten would weigh as one cat.
    val source = rememberGeoJsonSource(GeoJsonData.Features(cats))
    val inks = remember(colors.ground, colors.rim) { heatInks(ground = colors.ground, edge = colors.rim) }
    // A heatmap colours by density alone, never by a feature, so each fur colour is a layer of its own.
    inks.forEach { ink -> key(ink.key) { CoatHeat(source = source, ink = ink) } }
}

@Composable
private fun CoatHeat(source: GeoJsonSource, ink: HeatInk) {
    val radius = remember(ink.scale) { heatRadius(ink.scale) }
    val edgeRadius = remember(ink.scale) { heatRadius(ink.scale * EdgeReach) }
    // A little wider than the spot and faded out where the spot is dense, so it shows only as a rim.
    ink.edge?.let { edge ->
        InkLayer(
            id = "cat-${ink.key}-edge",
            source = source,
            key = ink.key,
            radius = edgeRadius,
            color = interpolate(
                linear(),
                heatmapDensity(),
                0 to const(edge.copy(alpha = 0f)),
                EdgeDensity to const(edge.copy(alpha = EdgeAlpha)),
                EdgeFadeDensity to const(edge.copy(alpha = 0f)),
            ),
        )
    }
    InkLayer(
        id = "cat-${ink.key}",
        source = source,
        key = ink.key,
        radius = radius,
        color = interpolate(
            linear(),
            heatmapDensity(),
            0 to const(ink.colour.copy(alpha = 0f)),
            HeatLowDensity to const(ink.colour.copy(alpha = HeatLowAlpha)),
            1 to const(ink.colour),
        ),
    )
}

@Composable
private fun InkLayer(
    id: String,
    source: GeoJsonSource,
    key: String,
    radius: Expression<NumberValue<Dp>>,
    color: Expression<ColorValue>,
) {
    HeatmapLayer(
        id = id,
        source = source,
        filter = feature.has(key),
        weight = feature[key].asNumber(),
        color = color,
        radius = radius,
        intensity = HeatIntensity,
        opacity = const(HeatOpacity),
    )
}
