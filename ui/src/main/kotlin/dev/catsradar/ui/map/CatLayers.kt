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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.expressions.dsl.and
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
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

internal const val POINT_COUNT = "point_count"

// Past street level clusters stop merging; cats sharing one fix still sit on one spot there.
private const val CLUSTER_MAX_ZOOM = 17

// Cats merge within a largest tile's width, so the photos of two clusters seldom overlap.
private val ClusterSpread = LargestTile.value.toInt()

// A font the tile server's glyphs include; a label in any other font never draws.
internal const val COUNT_FONT = "Noto Sans Bold"

private val DotRadius = 7.dp
internal val RimWidth = 1.5.dp
internal val DotSize = (DotRadius + RimWidth) * 2
private val ClusterRadius = 16.dp
internal val HalfTouchTarget = 24.dp
private val NoRoute = FeatureCollection<LineString, JsonObject>(emptyList())

internal val isCluster = feature.has(POINT_COUNT)

/** Colours read from the theme outside the map, whose layers compose without it. */
@Immutable
internal data class CatLayerColors(
    val rim: Color,
    val tileRim: Color,
    val ground: Color,
    val cluster: Color,
    val clusterCount: Color,
    val route: Color,
)

internal data class ClusterTap(val source: GeoJsonSource, val cluster: Feature<*, JsonObject?>)

@Composable
internal fun CatLayers(
    cats: FeatureCollection<Point, JsonObject>,
    photos: ImmutableList<String>,
    tileRound: Int,
    route: FeatureCollection<LineString, JsonObject>?,
    heat: Boolean,
    colors: CatLayerColors,
    onClusterTap: (ClusterTap) -> Unit,
    onCatsTap: (List<String>) -> Unit,
) {
    // Only while on, sparing a second parse of every cat; the dots it would sit under are hidden then.
    if (heat) CatHeat(cats = cats, colors = colors)
    OutingRoute(route = route, colors = colors)
    CatDots(cats, photos, tileRound, visible = !heat, colors, onClusterTap, onCatsTap)
}

/** The theme's colours for the map's layers, read here because the layers compose without the theme. */
@Composable
internal fun catLayerColors(): CatLayerColors {
    val scheme = MaterialTheme.colorScheme
    return CatLayerColors(
        rim = scheme.faceRim(),
        tileRim = scheme.surfaceBright,
        // The map style follows the theme, so its land is about as light or dark as the surface.
        ground = scheme.surface,
        cluster = scheme.primary,
        clusterCount = scheme.onPrimary,
        route = scheme.primary,
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
    photos: ImmutableList<String>,
    tileRound: Int,
    visible: Boolean,
    colors: CatLayerColors,
    onClusterTap: (ClusterTap) -> Unit,
    onCatsTap: (List<String>) -> Unit,
) {
    val source = rememberGeoJsonSource(
        GeoJsonData.Features(cats),
        GeoJsonOptions(
            cluster = true,
            clusterRadius = ClusterSpread,
            clusterMaxZoom = CLUSTER_MAX_ZOOM,
            clusterProperties = coverAggregator,
        ),
    )
    val uncovered = isCluster and !hasCover
    CircleLayer(
        id = "cat-clusters",
        source = source,
        filter = uncovered,
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
        filter = uncovered,
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
    CatPhotos(source, photos, tileRound, visible, colors, onClusterTap)
}

@Composable
private fun rememberCoatDots(colors: CatLayerColors) = remember(colors.rim) {
    fun dot(shares: List<ColourShare>) = image(CoatDotPainter(shares, colors.rim, RimWidth), DpSize(DotSize, DotSize))
    switch(
        feature[CAT_COAT].asString(const("")),
        CoatOption.entries.map { case(it.name, dot(dotShares(it))) },
        fallback = dot(dotShares(null)),
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
