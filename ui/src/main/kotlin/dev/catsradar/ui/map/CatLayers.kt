package dev.catsradar.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.catsradar.ui.coat.faceRim
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.heatmapDensity
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.HeatmapLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.GeoJsonSourceHandle
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point

private const val POINT_COUNT = "point_count"

// Past street level clusters stop merging; cats sharing one fix still sit on one spot there.
private const val CLUSTER_MAX_ZOOM = 17
private const val CLUSTER_RADIUS = 40

// A font the tile server's glyphs include; a label in any other font never draws.
private const val COUNT_FONT = "Noto Sans Bold"

private val DotRadius = 7.dp
private val ClusterRadius = 16.dp
private val HalfTouchTarget = 24.dp
private val HeatRadius = 28.dp
private const val HeatLowDensity = 0.3
private const val HeatOpacity = 0.85f
private const val HeatLowAlpha = 0.5f

private val NoRoute = FeatureCollection<LineString, JsonObject>(emptyList())

/** Colours read from the theme outside the map, whose layers compose without it. */
@Immutable
internal data class CatLayerColors(
    val unnoted: Color,
    val rim: Color,
    val cluster: Color,
    val clusterCount: Color,
    val route: Color,
    val heatLow: Color,
    val heatHigh: Color,
)

internal data class ClusterTap(val source: GeoJsonSource, val cluster: Feature<*, JsonObject?>)

@Composable
internal fun CatLayers(
    cats: FeatureCollection<Point, JsonObject>,
    route: FeatureCollection<LineString, JsonObject>?,
    heat: Boolean,
    colors: CatLayerColors,
    onClusterTap: (ClusterTap) -> Unit,
    onCatsTap: (List<String>) -> Unit,
) {
    // Only while on, sparing a second parse of every cat; the dots it would sit under are hidden then.
    if (heat) CatHeat(cats = cats, colors = colors)
    OutingRoute(route = route, colors = colors)
    CatDots(cats = cats, visible = !heat, colors = colors, onClusterTap = onClusterTap, onCatsTap = onCatsTap)
}

/** The theme's colours for the map's layers, read here because the layers compose without the theme. */
@Composable
internal fun catLayerColors(): CatLayerColors {
    val scheme = MaterialTheme.colorScheme
    return CatLayerColors(
        unnoted = scheme.primary,
        rim = scheme.faceRim(),
        cluster = scheme.primary,
        clusterCount = scheme.onPrimary,
        route = scheme.primary,
        heatLow = scheme.primary.copy(alpha = HeatLowAlpha),
        heatHigh = scheme.tertiary,
    )
}

@Composable
private fun CatHeat(cats: FeatureCollection<Point, JsonObject>, colors: CatLayerColors) {
    // Its own source, unclustered: over a clustered one, a cluster of ten would weigh as one cat.
    HeatmapLayer(
        id = "cat-heat",
        source = rememberGeoJsonSource(GeoJsonData.Features(cats)),
        color = interpolate(
            linear(),
            heatmapDensity(),
            0 to const(Color.Transparent),
            HeatLowDensity to const(colors.heatLow),
            1 to const(colors.heatHigh),
        ),
        radius = const(HeatRadius),
        opacity = const(HeatOpacity),
    )
}

@Composable
private fun OutingRoute(route: FeatureCollection<LineString, JsonObject>?, colors: CatLayerColors) {
    LineLayer(
        id = "outing-route",
        source = rememberGeoJsonSource(GeoJsonData.Features(route ?: NoRoute)),
        visible = route != null,
        color = const(colors.route),
        width = const(4.dp),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
}

@Composable
private fun CatDots(
    cats: FeatureCollection<Point, JsonObject>,
    visible: Boolean,
    colors: CatLayerColors,
    onClusterTap: (ClusterTap) -> Unit,
    onCatsTap: (List<String>) -> Unit,
) {
    val source = rememberGeoJsonSource(
        GeoJsonData.Features(cats),
        GeoJsonOptions(cluster = true, clusterRadius = CLUSTER_RADIUS, clusterMaxZoom = CLUSTER_MAX_ZOOM),
    )
    val isCluster = feature.has(POINT_COUNT)
    CircleLayer(
        id = "cat-clusters",
        source = source,
        filter = isCluster,
        visible = visible,
        color = const(colors.cluster),
        radius = const(ClusterRadius),
        strokeColor = const(colors.rim),
        strokeWidth = const(1.5.dp),
        hitPadding = HalfTouchTarget - ClusterRadius,
        onClick = { features ->
            features.firstOrNull()?.let { onClusterTap(ClusterTap(source, it)) }
            ClickResult.Consume
        },
    )
    SymbolLayer(
        id = "cat-cluster-counts",
        source = source,
        filter = isCluster,
        visible = visible,
        textField = format(span(feature[POINT_COUNT].convertToString())),
        textFont = const(listOf(COUNT_FONT)),
        textColor = const(colors.clusterCount),
        textSize = const(13.sp),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
    )
    CircleLayer(
        id = "cats",
        source = source,
        filter = !isCluster,
        visible = visible,
        color = feature[CAT_COLOR].convertToColor(),
        radius = const(DotRadius),
        strokeColor = const(colors.rim),
        strokeWidth = const(1.5.dp),
        hitPadding = HalfTouchTarget - DotRadius,
        onClick = { features ->
            onCatsTap(tappedCatIds(features))
            ClickResult.Consume
        },
    )
}

/** Zooms in until the cluster comes apart, or lists its cats when it never will. */
internal suspend fun MapState.open(tap: ClusterTap, onCatsTap: (List<String>) -> Unit) {
    val handle = style.sources[tap.source] ?: return
    val zoom = handle.query { getClusterExpansionZoom(tap.cluster) }
    val center = (tap.cluster.geometry as? Point)?.coordinates
    when {
        zoom == null -> Unit
        zoom <= CLUSTER_MAX_ZOOM && center != null ->
            animateCameraPosition(cameraPosition.copy(target = center, zoom = zoom))
        else -> handle.query { getClusterLeaves(tap.cluster, Long.MAX_VALUE, 0) }
            ?.let { leaves -> onCatsTap(tappedCatIds(leaves.features)) }
    }
}

// A cluster the source has rebuilt since the tap is gone, and the renderer fails the query for it.
@Suppress("TooGenericExceptionCaught", "SwallowedException") // the native binding reports any renderer error
private suspend fun <T> GeoJsonSourceHandle.query(block: suspend GeoJsonSourceHandle.() -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        null
    }
