package dev.catsradar.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.map.MapArea
import dev.catsradar.presentation.map.MapState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.faceRim
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox

// Vector tiles of OpenStreetMap data, free and keyless; the attribution the overlay draws is required.
private const val LightStyle = "https://tiles.openfreemap.org/styles/liberty"
private const val DarkStyle = "https://tiles.openfreemap.org/styles/dark"

private const val HALF_LUMINANCE = 0.5f

private val FitPadding = PaddingValues(48.dp)

@Composable
fun MapScreen(
    state: MapState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onCatsTap: (List<String>) -> Unit = {},
    onSpotDismiss: () -> Unit = {},
    onOutingFocus: (String) -> Unit = {},
    onFocusClear: () -> Unit = {},
) {
    when (state) {
        MapState.Loading -> Box(modifier = modifier.fillMaxSize())
        MapState.Empty -> EmptyMap(modifier = modifier.fillMaxSize().padding(contentPadding))
        is MapState.Located -> {
            CatsMap(
                state = state,
                contentPadding = contentPadding,
                modifier = modifier.fillMaxSize(),
                onCatsTap = onCatsTap,
                onFocusClear = onFocusClear,
            )
            state.spot?.let { spot ->
                MapSpotSheet(
                    spot = spot,
                    onCatClick = { id -> onCatsTap(listOf(id)) },
                    onOutingMapClick = onOutingFocus,
                    onDismiss = onSpotDismiss,
                )
            }
        }
    }
}

@Composable
private fun CatsMap(
    state: MapState.Located,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onCatsTap: (List<String>) -> Unit = {},
    onFocusClear: () -> Unit = {},
) {
    val colors = CatLayerColors(
        unnoted = MaterialTheme.colorScheme.primary,
        rim = MaterialTheme.colorScheme.faceRim(),
        cluster = MaterialTheme.colorScheme.primary,
        clusterCount = MaterialTheme.colorScheme.onPrimary,
        route = MaterialTheme.colorScheme.primary,
    )
    val cats = remember(state.points, colors.unnoted) { catFeatures(state.points, colors.unnoted) }
    val route = remember(state.points, state.focus) { state.focus?.let { routeLine(state.points) } }
    // Read from the scheme rather than the system, so the map follows whichever theme wraps it.
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF_LUMINANCE
    val style = BaseStyle.Uri(if (dark) DarkStyle else LightStyle)
    val tapCats by rememberUpdatedState(onCatsTap)
    var clusterTap by remember { mutableStateOf<ClusterTap?>(null) }
    val mapState = rememberMapState(baseStyle = style) {
        CatLayers(cats, route, colors, onClusterTap = { clusterTap = it }, onCatsTap = { tapCats(it) })
    }
    // Fitted once per map and focus, saved across recreation: the map restores its own camera, and a
    // cat located while it is up must not pull the view off where it was panned.
    var fitted by rememberSaveable { mutableStateOf(false) }
    var fittedFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = state.focus?.outingId
    val area by rememberUpdatedState(state.area)
    LaunchedEffect(mapState, focus) {
        if (fitted && fittedFocus == focus) return@LaunchedEffect
        if (fitted) {
            mapState.animateCameraToBounds(area.toBoundingBox(), padding = FitPadding)
        } else {
            mapState.fitCameraToBounds(area.toBoundingBox(), padding = FitPadding)
        }
        fitted = true
        fittedFocus = focus
    }
    LaunchedEffect(clusterTap) {
        val tap = clusterTap ?: return@LaunchedEffect
        mapState.open(tap, tapCats)
        clusterTap = null
    }
    val styleFailed = mapState.style.loadState is StyleLoadState.Failed
    val summary = pluralStringResource(R.plurals.map_summary, state.points.size, state.points.size)
    Box(modifier = modifier.semantics { contentDescription = summary }) {
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = mapState, cameraPadding = contentPadding)
        if (styleFailed) MapUnavailable(modifier = Modifier.fillMaxSize().padding(contentPadding))
        state.focus?.let { focused ->
            OutingFocusChip(
                label = focused.label,
                modifier = Modifier.align(Alignment.TopCenter).padding(contentPadding).padding(top = 12.dp),
                onClose = onFocusClear,
            )
        }
    }
}

@Composable
private fun OutingFocusChip(label: String, modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.map_focus_label, label), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.map_focus_close),
                )
            }
        }
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

@ThemePreviews
@Composable
private fun OutingFocusChipPreview() {
    CatsRadarTheme { OutingFocusChip(label = "Today, 14:10", modifier = Modifier.padding(16.dp)) }
}
