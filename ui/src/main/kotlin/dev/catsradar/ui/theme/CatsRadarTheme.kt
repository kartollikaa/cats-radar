package dev.catsradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** Public so surfaces drawn outside Compose UI — the home-screen widget — share the app's colours. */
val CatsRadarLightColors: ColorScheme = lightColorScheme()
val CatsRadarDarkColors: ColorScheme = darkColorScheme()

@Composable
fun CatsRadarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CatsRadarDarkColors else CatsRadarLightColors,
        content = content,
    )
}
