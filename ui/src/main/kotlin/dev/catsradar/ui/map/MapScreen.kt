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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.map.MapArea
import dev.catsradar.presentation.map.MapState
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.map.MapState as MaplibreMapState

@Composable
fun MapScreen(
    state: MapState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onCatsTap: (List<String>) -> Unit = {},
    onFocusClear: () -> Unit = {},
    onHeatToggle: () -> Unit = {},
    onCoatToggle: (CoatOption?) -> Unit = {},
    onCoatFilterClear: () -> Unit = {},
    onCatReach: () -> Unit = {},
) {
    when (state) {
        MapState.Loading -> Box(modifier = modifier.fillMaxSize())
        MapState.Empty -> EmptyMap(modifier = modifier.fillMaxSize().padding(contentPadding))
        is MapState.Located -> {
            var choosingCoats by rememberSaveable { mutableStateOf(false) }
            CatsMap(
                state = state,
                contentPadding = contentPadding,
                modifier = modifier.fillMaxSize(),
                onCatsTap = onCatsTap,
                onFocusClear = onFocusClear,
                onHeatToggle = onHeatToggle,
                onCoatsClick = { choosingCoats = true },
                onCatReach = onCatReach,
            )
            if (choosingCoats) {
                MapCoatSheet(
                    shown = state.shownCoats,
                    onCoatToggle = onCoatToggle,
                    onClear = onCoatFilterClear,
                    onDismiss = { choosingCoats = false },
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
    onHeatToggle: () -> Unit = {},
    onCoatsClick: () -> Unit = {},
    onCatReach: () -> Unit = {},
) {
    val colors = catLayerColors()
    val cats = remember(state.points) { catFeatures(state.points) }
    val route = remember(state.focus) { state.focus?.let { routeLines(it.lines) } }
    val style = mapStyle()
    val tapCats by rememberUpdatedState(onCatsTap)
    var clusterTap by remember { mutableStateOf<ClusterTap?>(null) }
    val mapState = rememberMapState(baseStyle = style) {
        CatLayers(cats, route, state.heat, colors, onClusterTap = { clusterTap = it }, onCatsTap = { tapCats(it) })
    }
    MapCamera(
        mapState,
        area = state.area,
        focus = state.focus?.outingId,
        catArea = state.catArea,
        onCatReach = onCatReach,
    )
    LaunchedEffect(clusterTap) {
        val tap = clusterTap ?: return@LaunchedEffect
        mapState.open(tap, tapCats)
        clusterTap = null
    }
    val styleFailed = mapState.style.loadState is StyleLoadState.Failed
    val summary = pluralStringResource(R.plurals.map_summary, state.points.size, state.points.size)
    Box(modifier = modifier.semantics { contentDescription = summary }) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            state = mapState,
            cameraPadding = contentPadding,
            overlay = { MapAttribution(contentPadding) },
        )
        if (styleFailed) MapUnavailable(modifier = Modifier.fillMaxSize().padding(contentPadding))
        MapOverlay(
            state = state,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            onFocusClear = onFocusClear,
            onHeatToggle = onHeatToggle,
            onCoatsClick = onCoatsClick,
        )
    }
}

// Fitted once per map and focus, and moved once per requested cat, saved across recreation: the map restores
// its own camera, and a cat located while it is up must not pull the view off where it was panned.
@Composable
private fun MapCamera(
    mapState: MaplibreMapState,
    area: MapArea,
    focus: String?,
    catArea: MapArea?,
    onCatReach: () -> Unit,
) {
    var fitted by rememberSaveable { mutableStateOf(false) }
    var fittedFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val latestArea by rememberUpdatedState(area)
    val catReach by rememberUpdatedState(onCatReach)
    LaunchedEffect(mapState, focus, catArea) {
        if (catArea != null) {
            moveOnce(move = { mapState.moveTo(catArea, animate = fitted) }) {
                fitted = true
                fittedFocus = focus
                catReach()
            }
            return@LaunchedEffect
        }
        if (fitted && fittedFocus == focus) return@LaunchedEffect
        mapState.moveTo(latestArea, animate = fitted)
        fitted = true
        fittedFocus = focus
    }
}

// A pan cancels only the map's own camera call and still ends the request, or a later focus change would
// resume it. A cancelled caller is the map leaving mid-move, which reports nothing.
internal suspend fun moveOnce(move: suspend () -> Unit, onReach: () -> Unit) {
    try {
        move()
    } catch (interrupted: CancellationException) {
        if (!currentCoroutineContext().isActive) throw interrupted
    }
    onReach()
}

private suspend fun MaplibreMapState.moveTo(area: MapArea, animate: Boolean) {
    if (animate) {
        animateCameraToBounds(area.toBoundingBox(), padding = FitPadding)
    } else {
        fitCameraToBounds(area.toBoundingBox(), padding = FitPadding)
    }
}

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
