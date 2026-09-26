package dev.catsradar.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.catsradar.presentation.map.MapArea
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox

// Vector tiles of OpenStreetMap data, free and keyless.
private const val LightStyle = "https://tiles.openfreemap.org/styles/liberty"
private const val DarkStyle = "https://tiles.openfreemap.org/styles/dark"

private const val HALF_LUMINANCE = 0.5f

internal val FitPadding = PaddingValues(48.dp)

/** The map's style for the theme wrapping it: read from the scheme, not the system. */
@Composable
internal fun mapStyle(): BaseStyle {
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF_LUMINANCE
    return BaseStyle.Uri(if (dark) DarkStyle else LightStyle)
}

internal fun MapArea.toBoundingBox() = BoundingBox(west, south, east, north)

// The map's content is a layer on the style, so without the style there is nothing to draw.
@Composable
internal fun MapUnavailable(modifier: Modifier = Modifier) {
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

@ThemePreviews
@Composable
private fun MapUnavailablePreview() {
    CatsRadarTheme { MapUnavailable() }
}
