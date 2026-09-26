package dev.catsradar.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.and
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.gte
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.dsl.lt
import org.maplibre.compose.expressions.dsl.min
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.StringValue
import org.maplibre.compose.expressions.value.TranslateAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.ResolvedStyleImage
import org.maplibre.compose.sources.GeoJsonOptions.ClusterPropertyAggregator
import org.maplibre.compose.sources.GeoJsonSource
import kotlin.time.Duration.Companion.milliseconds

private const val COVER_RANK = "cover_rank"

// Beyond any photo's rank, yet held exactly by map expressions, which compute in floats.
private const val NO_COVER = 1 shl 24

private const val IMAGE_CHECKS = 50
private val ImageCheckInterval = 50.milliseconds

private const val SMALL_TILE_ZOOM = 11
private const val LARGE_TILE_ZOOM = 16

private val SmallestTile = 20.dp
internal val LargestTile = 44.dp
private val TileCorner = 11.dp
private val TileRim = 3.dp
private val BadgeRadius = 9.dp

internal val coverAggregator = mapOf(
    COVER_RANK to ClusterPropertyAggregator(
        mapper = feature[CAT_PHOTO_RANK].asNumber(const(NO_COVER)),
        reducer = min(feature.accumulated().asNumber(), feature[COVER_RANK].asNumber()),
    ),
)

internal val hasCover = feature[COVER_RANK].asNumber(const(NO_COVER)) lt const(NO_COVER)

private val TileScale = interpolate(
    linear(),
    zoom(),
    SMALL_TILE_ZOOM to const(SmallestTile / LargestTile),
    LARGE_TILE_ZOOM to const(1f),
)

private val BadgeOffset = interpolate(
    linear(),
    zoom(),
    SMALL_TILE_ZOOM to const(DpOffset(SmallestTile / 2, -SmallestTile / 2)),
    LARGE_TILE_ZOOM to const(DpOffset(LargestTile / 2 - BadgeRadius / 2, -LargestTile / 2 + BadgeRadius / 2)),
)

/** [photos] must be [photoImages] of the points in [source]; [tilesAdded] counts the tiles the map holds. */
@Composable
internal fun CatPhotos(
    source: GeoJsonSource,
    photos: ImmutableList<String>,
    tilesAdded: Int,
    visible: Boolean,
    colors: CatLayerColors,
    onClusterTap: (ClusterTap) -> Unit,
    onCatsTap: (List<String>) -> Unit,
) {
    SymbolLayer(
        id = "cat-photos",
        source = source,
        filter = !isCluster and feature.has(CAT_PHOTO) and layOutAgainAfter(tilesAdded),
        visible = visible,
        sortKey = feature[CAT_PHOTO_RANK].asNumber(),
        iconImage = image(feature[CAT_PHOTO].asString()),
        iconSize = TileScale,
        hitPadding = HalfTouchTarget - SmallestTile / 2,
        onClick = { features ->
            onCatsTap(tappedCatIds(features))
            ClickResult.Consume
        },
    )
    // Never gives way: its badge, a layer of its own, could not follow it out.
    SymbolLayer(
        id = "cat-cluster-photos",
        source = source,
        filter = isCluster and hasCover and layOutAgainAfter(tilesAdded),
        visible = visible,
        iconImage = image(rememberCovers(photos)),
        iconSize = TileScale,
        iconAllowOverlap = const(true),
        hitPadding = HalfTouchTarget - SmallestTile / 2,
        onClick = { features ->
            features.firstOrNull()?.let { onClusterTap(ClusterTap(source, it)) }
            ClickResult.Consume
        },
    )
    CircleLayer(
        id = "cat-cluster-badges",
        source = source,
        filter = isCluster and hasCover,
        visible = visible,
        color = const(colors.cluster),
        radius = const(BadgeRadius),
        strokeColor = const(colors.rim),
        strokeWidth = const(RimWidth),
        translate = BadgeOffset,
        translateAnchor = const(TranslateAnchor.Viewport),
    )
    SymbolLayer(
        id = "cat-cluster-badge-counts",
        source = source,
        filter = isCluster and hasCover,
        visible = visible,
        textField = format(span(feature[POINT_COUNT].convertToString())),
        textFont = const(listOf(COUNT_FONT)),
        textColor = const(colors.clusterCount),
        textSize = const(10.sp),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
        textTranslate = BadgeOffset,
        textTranslateAnchor = const(TranslateAnchor.Viewport),
    )
}

// Always true; each count makes a new filter, which lays out again a tile laid out before its photo arrived.
// It reads a feature so that the map cannot fold it into a constant.
private fun layOutAgainAfter(tilesAdded: Int) =
    feature[CAT_PHOTO_RANK].asNumber(const(0)) gte const(-1 - tilesAdded)

@Composable
private fun rememberCovers(photos: ImmutableList<String>): Expression<StringValue> = remember(photos) {
    if (photos.isEmpty()) {
        const("")
    } else {
        switch(
            feature[COVER_RANK].asNumber(),
            photos.mapIndexed { rank, photo -> case(rank, const(photo)) },
            fallback = const(""),
        )
    }
}

/** What drawing photo tiles has taught the map: the thumbnails that are no image, and how many tiles it holds. */
@Stable
internal class PhotoTileProgress {
    var unreadable by mutableStateOf(persistentSetOf<String>())
        private set
    var added by mutableIntStateOf(0)
        private set

    fun markUnreadable(thumbnail: String) {
        unreadable = unreadable.adding(thumbnail)
    }

    fun countAdded() {
        added++
    }
}

/** Draws each photo tile the first time the map asks for it, and tells [progress] how it went. */
@Composable
internal fun PhotoTiles(mapState: MapState, rim: Color, progress: PhotoTileProgress) {
    val density = LocalDensity.current
    val tile = remember(density, rim) {
        with(density) { PhotoTile(LargestTile.roundToPx(), TileCorner.toPx(), TileRim.toPx(), rim) }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(mapState, tile, progress) {
        mapState.missingImageResolver = resolver@{ id ->
            val thumbnail = thumbnailOf(id) ?: return@resolver null
            val drawn = tile.draw(thumbnail)
            if (drawn == null) {
                progress.markUnreadable(thumbnail)
                return@resolver null
            }
            // The map adds the tile only after this returns, and counting it sooner lays tiles out too early.
            scope.launch { if (mapState.awaitImage(id)) progress.countAdded() }
            ResolvedStyleImage(drawn)
        }
    }
}

private suspend fun MapState.awaitImage(id: String): Boolean {
    repeat(IMAGE_CHECKS) {
        if (style.images[id] != null) return true
        delay(ImageCheckInterval)
    }
    return false
}
