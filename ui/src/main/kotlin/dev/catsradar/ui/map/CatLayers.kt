package dev.catsradar.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.ui.coat.faceRim
import dev.catsradar.ui.coat.look
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.heatmapDensity
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
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
private val RimWidth = 1.5.dp
private val DotSize = (DotRadius + RimWidth) * 2
private val ClusterRadius = 16.dp
private val HalfTouchTarget = 24.dp
private const val HeatLowDensity = 0.3
private const val HeatOpacity = 0.85f
private const val HeatLowAlpha = 0.7f
private const val HaloDensity = 0.1
private const val HaloAlpha = 0.35f

// Each cat's heat is sized and weighted by zoom: at one size for every zoom, a city's cats merge into one blob.
private val HeatRadius =
    interpolate(linear(), zoom(), 9 to const(8.dp), 11 to const(16.dp), 14 to const(24.dp), 16 to const(30.dp))
private val HeatIntensity = interpolate(linear(), zoom(), 9 to const(0.7f), 15 to const(1f))

private val NoRoute = FeatureCollection<LineString, JsonObject>(emptyList())

/** Colours read from the theme outside the map, whose layers compose without it. */
@Immutable
internal data class CatLayerColors(
    val unnoted: Color,
    val rim: Color,
    val cluster: Color,
    val clusterCount: Color,
    val route: Color,
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
    )
}

@Composable
private fun CatHeat(cats: FeatureCollection<Point, JsonObject>, colors: CatLayerColors) {
    // Its own source, unclustered: over a clustered one, a cluster of ten would weigh as one cat.
    val source = rememberGeoJsonSource(GeoJsonData.Features(cats))
    // A heatmap colours by density alone, never by a feature, so each fur colour is a layer of its own.
    HeatmapLayer(
        id = "cat-heat-halo",
        source = source,
        color = interpolate(
            linear(),
            heatmapDensity(),
            0 to const(colors.rim.copy(alpha = 0f)),
            HaloDensity to const(colors.rim.copy(alpha = HaloAlpha)),
        ),
        radius = HeatRadius,
        intensity = HeatIntensity,
        opacity = const(HeatOpacity),
    )
    CoatHeat(source = source, key = UNNOTED_HEAT, colour = colors.unnoted)
    CoatHeatColours.forEach { colour -> CoatHeat(source = source, key = heatKey(colour), colour = colour) }
}

@Composable
private fun CoatHeat(source: GeoJsonSource, key: String, colour: Color) {
    HeatmapLayer(
        id = "cat-$key",
        source = source,
        filter = feature.has(key),
        weight = feature[key].asNumber(),
        color = interpolate(
            linear(),
            heatmapDensity(),
            0 to const(colour.copy(alpha = 0f)),
            HeatLowDensity to const(colour.copy(alpha = HeatLowAlpha)),
            1 to const(colour),
        ),
        radius = HeatRadius,
        intensity = HeatIntensity,
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
        strokeWidth = const(RimWidth),
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
    SymbolLayer(
        id = "cats",
        source = source,
        filter = !isCluster,
        visible = visible,
        iconImage = rememberCoatDots(colors),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        hitPadding = HalfTouchTarget - DotSize / 2,
        onClick = { features ->
            onCatsTap(tappedCatIds(features))
            ClickResult.Consume
        },
    )
}

@Composable
private fun rememberCoatDots(colors: CatLayerColors) = remember(colors.rim, colors.unnoted) {
    fun dot(shares: List<ColourShare>) = image(CoatDotPainter(shares, colors.rim, RimWidth), DpSize(DotSize, DotSize))
    switch(
        feature[CAT_COAT].asString(const("")),
        CoatOption.entries.map { case(it.name, dot(it.look().shares())) },
        fallback = dot(listOf(ColourShare(colors.unnoted, 1.0))),
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
