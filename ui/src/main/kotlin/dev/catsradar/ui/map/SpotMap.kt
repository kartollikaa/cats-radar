package dev.catsradar.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.faceRim
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
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

const val SpotMapTestTag = "spot-map"
const val CatDotTestTag = "cat-dot"

// Small enough for the whole line to fit across a phone-wide card.
private val SmallAttribution = AttributionDefaults.expandedStyle()
    .copy(textStyle = AttributionDefaults.ContentTextStyle.copy(fontSize = 10.sp))

// A texture view, unlike the default surface view, clips to rounded corners and scrolls with the screen.
private val StillMap = MapUiOptions(from = MapUiOptions.None) { renderMode = AndroidRenderMode.Texture }

/**
 * A map centred on [position] with the cat's dot on that point, in the colours of its [coat] as the Map
 * tab draws it. It takes no gestures: a tap or a drag on it reaches whatever holds it.
 */
@Composable
internal fun SpotMap(position: MapPosition, coat: CoatOption?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        // The map's native runtime cannot start in a preview or a JVM test.
        if (LocalInspectionMode.current) {
            Box(modifier = Modifier.matchParentSize().clearAndSetSemantics { testTag = SpotMapTestTag })
            CentreDot(coat)
        } else {
            // Built afresh at a new position rather than moved there, so it never shows a stale spot.
            key(position) { TileMap(position, coat) }
        }
    }
}

@Composable
private fun BoxScope.TileMap(position: MapPosition, coat: CoatOption?) {
    val camera = CameraPosition(target = Position(position.longitude, position.latitude), zoom = StreetZoom)
    val mapState = rememberMapState(baseStyle = mapStyle(), initialCameraPosition = camera)
    MaplibreMap(
        modifier = Modifier.matchParentSize().clearAndSetSemantics { testTag = SpotMapTestTag },
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
        CentreDot(coat)
        // The tiles' licence requires it; TalkBack skips it, or the card would read it before the place.
        CompositionLocalProvider(LocalMapState provides mapState) {
            ExpandingAttributionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(MaplibreMapOverlay.Spacing)
                    .clearAndSetSemantics {},
                // Text without the default's links: a tap anywhere on this map belongs to whatever holds it.
                expandedContent = { attributions, textStyle -> BasicText(plainText(attributions), style = textStyle) },
                expandedStyle = SmallAttribution,
            )
        }
    }
}

private fun plainText(attributions: List<String>) = attributions.joinToString(" ") { AnnotatedString.fromHtml(it).text }

@Composable
private fun BoxScope.CentreDot(coat: CoatOption?) {
    CatDot(coat, modifier = Modifier.align(Alignment.Center))
}

@Composable
private fun CatDot(coat: CoatOption?, modifier: Modifier = Modifier) {
    val rim = MaterialTheme.colorScheme.faceRim()
    val painter = remember(coat, rim) { CoatDotPainter(dotShares(coat), rim, RimWidth) }
    Image(painter = painter, contentDescription = null, modifier = modifier.size(DotSize).testTag(CatDotTestTag))
}

@ThemePreviews
@Composable
private fun SpotMapPreview() {
    CatsRadarTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SpotMap(
                position = MapPosition(latitude = 41.39864, longitude = 2.17842),
                coat = CoatOption.GINGER_WHITE,
                modifier = Modifier.width(320.dp).aspectRatio(2f),
            )
            SpotMap(
                position = MapPosition(latitude = 41.39864, longitude = 2.17842),
                coat = null,
                modifier = Modifier.width(320.dp).aspectRatio(2f),
            )
        }
    }
}
