package dev.catsradar.ui.theme

import androidx.compose.ui.graphics.Color

// A photo is judged against black whatever the app's theme, as galleries show it.
internal object ViewerColors {
    val Stage = Color.Black
    val OnStage = Color.White
    val ChromeScrim = Color.Black.copy(alpha = 0.5f)
}
