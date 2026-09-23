package dev.catsradar.app.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.catsradar.ui.theme.catsRadarColorScheme

/** The wallpaper's colours on Android 12 and later, the app's own teal below it. */
fun deviceColorScheme(context: Context, darkTheme: Boolean): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        catsRadarColorScheme(darkTheme)
    }

@Composable
fun rememberDeviceColorScheme(): ColorScheme {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    return remember(context, darkTheme) { deviceColorScheme(context, darkTheme) }
}
