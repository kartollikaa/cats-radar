package dev.catsradar.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.faceRim
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.AndroidRenderMode
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.map.renderMode
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.spatialk.geojson.Position
import org.maplibre.compose.overlay.MapOverlay as MaplibreMapOverlay

private const val StreetZoom = 15.0

private val PinSize = DpSize(28.dp, 40.dp)

// A cubic Bézier's handle, in radii, that draws a quarter circle.
private const val CircleKappa = 0.5523f

// In radii below the pin's top: where the tail's sides curve in towards the point.
private const val ShoulderDrop = 1.75f
private const val PinDotShare = 0.36f

const val PinnedMapTestTag = "pinned-map"
const val MapPinTestTag = "map-pin"

// Small enough for the whole line to fit across a phone-wide card.
private val SmallAttribution = AttributionDefaults.expandedStyle()
    .copy(textStyle = AttributionDefaults.ContentTextStyle.copy(fontSize = 10.sp))

// A texture view, unlike the default surface view, clips to rounded corners and scrolls with the screen.
private val StillMap = MapUiOptions(from = MapUiOptions.None) { renderMode = AndroidRenderMode.Texture }

/**
 * A map centred on [position] with a pin on that point. It takes no gestures: a tap or a drag on it
 * reaches whatever holds it.
 */
@Composable
internal fun PinnedMap(position: MapPosition, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        // The map's native runtime cannot start in a preview or a JVM test.
        if (LocalInspectionMode.current) {
            Box(modifier = Modifier.matchParentSize().clearAndSetSemantics { testTag = PinnedMapTestTag })
            CentrePin()
        } else {
            TileMap(position)
        }
    }
}

@Composable
private fun BoxScope.TileMap(position: MapPosition) {
    val camera = CameraPosition(target = Position(position.longitude, position.latitude), zoom = StreetZoom)
    val mapState = rememberMapState(baseStyle = themedMapStyle(), initialCameraPosition = camera)
    LaunchedEffect(mapState, camera) { mapState.animateCameraPosition(camera, CameraAnimation.Ease()) }
    MaplibreMap(
        modifier = Modifier.matchParentSize().clearAndSetSemantics { testTag = PinnedMapTestTag },
        state = mapState,
        interactions = MapInteractions.None,
        uiOptions = StillMap,
        overlay = {},
    )
    if (mapState.style.loadState is StyleLoadState.Failed) {
        Text(
            text = stringResource(R.string.map_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(16.dp),
        )
    } else {
        CentrePin()
        // The tiles' licence requires this attribution wherever they show.
        CompositionLocalProvider(LocalMapState provides mapState) {
            ExpandingAttributionButton(
                modifier = Modifier.align(Alignment.BottomEnd).padding(MaplibreMapOverlay.Spacing),
                expandedStyle = SmallAttribution,
            )
        }
    }
}

@Composable
private fun BoxScope.CentrePin() {
    MapPin(modifier = Modifier.align(Alignment.Center).offset(y = -PinSize.height / 2))
}

/** A pin whose point is the bottom centre of its bounds. */
@Composable
private fun MapPin(modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.onPrimary
    val rim = MaterialTheme.colorScheme.faceRim()
    Canvas(modifier = modifier.size(PinSize).testTag(MapPinTestTag)) {
        val pin = pinPath(size)
        val radius = size.width / 2
        drawPath(pin, fill)
        drawPath(pin, rim, style = Stroke(RimWidth.toPx()))
        drawCircle(dot, radius = radius * PinDotShare, center = Offset(radius, radius))
    }
}

private fun pinPath(size: Size): Path {
    val radius = size.width / 2
    val handle = radius * CircleKappa
    val shoulder = radius * ShoulderDrop
    return Path().apply {
        moveTo(radius, 0f)
        cubicTo(radius - handle, 0f, 0f, radius - handle, 0f, radius)
        cubicTo(0f, shoulder, radius, size.height, radius, size.height)
        cubicTo(radius, size.height, size.width, shoulder, size.width, radius)
        cubicTo(size.width, radius - handle, radius + handle, 0f, radius, 0f)
        close()
    }
}

@ThemePreviews
@Composable
private fun PinnedMapPreview() {
    CatsRadarTheme {
        PinnedMap(
            position = MapPosition(latitude = 41.39864, longitude = 2.17842),
            modifier = Modifier.width(320.dp).aspectRatio(2f),
        )
    }
}

@ThemePreviews
@Composable
private fun MapPinPreview() {
    CatsRadarTheme { MapPin(modifier = Modifier.padding(16.dp)) }
}
