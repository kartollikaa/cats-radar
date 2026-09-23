package dev.catsradar.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.map.MapArea
import dev.catsradar.presentation.map.MapPoint
import dev.catsradar.presentation.map.MapState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.faceRim
import dev.catsradar.ui.coat.look
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

// Vector tiles of OpenStreetMap data, free and keyless; the attribution the overlay draws is required.
private const val LightStyle = "https://tiles.openfreemap.org/styles/liberty"
private const val DarkStyle = "https://tiles.openfreemap.org/styles/dark"

private const val CAT_COLOR = "color"
private const val HALF_LUMINANCE = 0.5f

@Composable
fun MapScreen(
    state: MapState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    when (state) {
        MapState.Loading -> Box(modifier = modifier.fillMaxSize())
        MapState.Empty -> EmptyMap(modifier = modifier.fillMaxSize().padding(contentPadding))
        is MapState.Located -> CatsMap(
            state = state,
            contentPadding = contentPadding,
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CatsMap(state: MapState.Located, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val unnoted = MaterialTheme.colorScheme.primary
    // The coat faces' own rim: a white cat's dot would otherwise vanish into a light street.
    val rim = MaterialTheme.colorScheme.faceRim()
    val cats = remember(state.points, unnoted) { state.points.toFeatures(unnoted) }
    // Read from the scheme rather than the system, so the map follows whichever theme wraps it.
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF_LUMINANCE
    val style = BaseStyle.Uri(if (dark) DarkStyle else LightStyle)
    val mapState = rememberMapState(baseStyle = style) {
        CircleLayer(
            id = "cats",
            source = rememberGeoJsonSource(GeoJsonData.Features(cats)),
            color = feature[CAT_COLOR].convertToColor(),
            radius = const(7.dp),
            strokeColor = const(rim),
            strokeWidth = const(1.5.dp),
        )
    }
    // Fitted once per map, saved across recreation: the map restores its own camera, and a cat located
    // while it is up must not pull the view off where it was panned.
    var fitted by rememberSaveable { mutableStateOf(false) }
    val area by rememberUpdatedState(state.area)
    LaunchedEffect(mapState) {
        if (!fitted) {
            mapState.fitCameraToBounds(area.toBoundingBox(), padding = PaddingValues(48.dp))
            fitted = true
        }
    }
    val styleFailed = mapState.style.loadState is StyleLoadState.Failed
    val summary = pluralStringResource(R.plurals.map_summary, state.points.size, state.points.size)
    Box(modifier = modifier.semantics { contentDescription = summary }) {
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = mapState, cameraPadding = contentPadding)
        if (styleFailed) MapUnavailable(modifier = Modifier.fillMaxSize().padding(contentPadding))
    }
}

// The dots are a layer on the style, so without the style there is nothing to draw them on.
@Composable
private fun MapUnavailable(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.map_unavailable),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.map_unavailable_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun ImmutableList<MapPoint>.toFeatures(unnoted: Color): FeatureCollection<Point, JsonObject> =
    FeatureCollection(
        map { point ->
            val color = point.coat?.look()?.fur ?: unnoted
            Feature(
                Point(Position(point.longitude, point.latitude)),
                buildJsonObject { put(CAT_COLOR, color.toHex()) },
            )
        },
    )

private fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private fun MapArea.toBoundingBox() = BoundingBox(west, south, east, north)

@Composable
private fun EmptyMap(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nav_map),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.map_empty),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.map_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@ThemePreviews
@Composable
private fun MapScreenEmptyPreview() {
    CatsRadarTheme {
        Surface { MapScreen(state = MapState.Empty) }
    }
}

@ThemePreviews
@Composable
private fun MapUnavailablePreview() {
    CatsRadarTheme { MapUnavailable() }
}
