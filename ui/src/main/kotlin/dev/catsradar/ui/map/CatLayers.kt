package dev.catsradar.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.GeoJsonSourceHandle
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
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

/** Colours read from the theme outside the map, whose layers compose without it. */
@Immutable
internal data class CatLayerColors(val unnoted: Color, val rim: Color, val cluster: Color, val clusterCount: Color)

internal data class ClusterTap(val source: GeoJsonSource, val cluster: Feature<*, JsonObject?>)

@Composable
internal fun CatLayers(
    cats: FeatureCollection<Point, JsonObject>,
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
