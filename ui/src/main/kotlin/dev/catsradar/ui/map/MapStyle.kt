package dev.catsradar.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import org.maplibre.compose.style.BaseStyle

// Vector tiles of OpenStreetMap data, free and keyless.
private const val LightStyle = "https://tiles.openfreemap.org/styles/liberty"
private const val DarkStyle = "https://tiles.openfreemap.org/styles/dark"

private const val HALF_LUMINANCE = 0.5f

/** The tiles' light or dark style, following whichever theme wraps the map rather than the system's. */
@Composable
internal fun themedMapStyle(): BaseStyle {
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF_LUMINANCE
    return BaseStyle.Uri(if (dark) DarkStyle else LightStyle)
}
