package dev.catsradar.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.AttributionStyle
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.overlay.MapOverlay as MaplibreMapOverlay

internal fun attributionStyle(
    contentColor: Color = AttributionDefaults.ContentColor,
    textStyle: TextStyle = AttributionDefaults.ContentTextStyle,
): AttributionStyle = AttributionDefaults.expandedStyle().copy(
    containerColor = contentColor.copy(alpha = 0f),
    contentColor = contentColor,
    textStyle = textStyle,
)

@Composable
internal fun AttributionButton(contentColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = AttributionDefaults.icon(),
            contentDescription = AttributionDefaults.contentDescription(),
            modifier = Modifier.size(24.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(contentColor),
        )
    }
}

// The tiles' licence requires this attribution; the MapLibre logo the library's own overlays add is optional.
@Composable
internal fun MapAttribution(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomEnd,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(MaplibreMapOverlay.Spacing),
    ) {
        ExpandingAttributionButton(
            modifier = Modifier.align(alignment),
            toggleButton = { onClick -> AttributionButton(contentColor, onClick) },
            expandedStyle = attributionStyle(contentColor = contentColor),
        )
    }
}
