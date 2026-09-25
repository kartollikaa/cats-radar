package dev.catsradar.ui.locationpicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.locationpicker.LocationPickerState
import dev.catsradar.presentation.map.MapArea
import dev.catsradar.ui.R
import dev.catsradar.ui.components.CenterAppBar
import dev.catsradar.ui.components.CenterAppBarDefaults
import dev.catsradar.ui.map.FitPadding
import dev.catsradar.ui.map.MapAttribution
import dev.catsradar.ui.map.MapUnavailable
import dev.catsradar.ui.map.mapStyle
import dev.catsradar.ui.map.moveOnce
import dev.catsradar.ui.map.toBoundingBox
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.map.MapState as MaplibreMapState

private val PinSize = 48.dp

/** The point under the pin when Save was tapped. */
data class SaveLocationInteraction(val latitude: Double, val longitude: Double)

@Composable
fun LocationPickerScreen(
    state: LocationPickerState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onBackClick: () -> Unit = {},
    onWhereAmIClick: () -> Unit = {},
    onSaveClick: (SaveLocationInteraction) -> Unit = {},
    onMoveReach: () -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val barPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateTopPadding(),
        end = contentPadding.calculateEndPadding(layoutDirection),
    )
    Box(modifier = modifier.fillMaxSize()) {
        if (state is LocationPickerState.Picking) {
            PickerMap(
                state = state,
                contentPadding = contentPadding,
                onWhereAmIClick = onWhereAmIClick,
                onSaveClick = onSaveClick,
                onMoveReach = onMoveReach,
            )
        }
        CenterAppBar(
            modifier = Modifier.padding(barPadding),
            startContent = {
                FilledTonalIconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.picker_back),
                    )
                }
            },
        )
    }
}

@Composable
private fun PickerMap(
    state: LocationPickerState.Picking,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onWhereAmIClick: () -> Unit = {},
    onSaveClick: (SaveLocationInteraction) -> Unit = {},
    onMoveReach: () -> Unit = {},
) {
    val mapState = rememberMapState(baseStyle = mapStyle())
    var placed by rememberSaveable { mutableStateOf(false) }
    PickerCamera(
        mapState,
        start = state.start.takeUnless { placed },
        moveTo = state.moveTo,
        onPlace = { placed = true },
        onMoveReach = onMoveReach,
    )
    val mapDescription = stringResource(R.string.picker_map_description)
    val layoutDirection = LocalLayoutDirection.current
    val belowBar = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateTopPadding() + CenterAppBarDefaults.Height,
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = contentPadding.calculateBottomPadding(),
    )
    Box(modifier = modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize().semantics { contentDescription = mapDescription },
            state = mapState,
            cameraPadding = contentPadding,
            overlay = { MapAttribution(belowBar, alignment = Alignment.TopEnd) },
        )
        if (mapState.style.loadState is StyleLoadState.Failed) {
            MapUnavailable(modifier = Modifier.fillMaxSize().padding(belowBar))
        }
        // The camera's target is the centre of its padded viewport, so the pin's tip sits there.
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_pin),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(PinSize).offset(y = -PinSize / 2),
            )
        }
        LocationPickerControls(
            saving = state.saving,
            locating = state.locating,
            // Until the camera is placed its target is MapLibre's default, not a point anyone looked at.
            mapReady = placed && mapState.style.loadState == StyleLoadState.Ready,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(16.dp),
            onWhereAmIClick = onWhereAmIClick,
            onSaveClick = {
                val target = mapState.cameraPosition.target
                onSaveClick(SaveLocationInteraction(latitude = target.latitude, longitude = target.longitude))
            },
        )
    }
}

// Placed once: the map saves its own camera across recreation, and only a move the Store asks for may take it
// anywhere after that.
@Composable
private fun PickerCamera(
    mapState: MaplibreMapState,
    start: MapArea?,
    moveTo: MapArea?,
    onPlace: () -> Unit,
    onMoveReach: () -> Unit,
) {
    val place by rememberUpdatedState(onPlace)
    val moveReach by rememberUpdatedState(onMoveReach)
    LaunchedEffect(mapState) {
        start?.let { mapState.fitCameraToBounds(it.toBoundingBox(), padding = FitPadding) }
        mapState.awaitViewport()
        place()
    }
    LaunchedEffect(mapState, moveTo) {
        val area = moveTo ?: return@LaunchedEffect
        moveOnce(move = { mapState.animateCameraToBounds(area.toBoundingBox(), padding = FitPadding) }) {
            moveReach()
        }
    }
}

@Composable
fun LocationPickerControls(
    saving: Boolean,
    locating: Boolean,
    modifier: Modifier = Modifier,
    mapReady: Boolean = true,
    onWhereAmIClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                text = stringResource(R.string.picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onSaveClick, enabled = mapReady && !saving, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.picker_save))
            }
            FilledTonalIconButton(onClick = onWhereAmIClick, enabled = mapReady && !locating) {
                Icon(
                    painter = painterResource(R.drawable.ic_my_location),
                    contentDescription = stringResource(R.string.picker_where_am_i),
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun LocationPickerControlsPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LocationPickerControls(saving = false, locating = false, modifier = Modifier.padding(16.dp))
            LocationPickerControls(saving = true, locating = true, modifier = Modifier.padding(16.dp))
        }
    }
}
