package dev.catsradar.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.overlay.MapOverlay as MaplibreMapOverlay

// The tiles' licence requires this attribution; the MapLibre logo the library's own overlays add is optional.
@Composable
internal fun MapAttribution(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomEnd,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(MaplibreMapOverlay.Spacing),
    ) {
        ExpandingAttributionButton(modifier = Modifier.align(alignment))
    }
}
