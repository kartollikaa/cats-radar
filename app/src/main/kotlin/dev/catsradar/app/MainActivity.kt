package dev.catsradar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.catsradar.app.navigation.CatsRadarNavHost
import dev.catsradar.ui.theme.CatsRadarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatsRadarTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    CatsRadarNavHost()
                }
            }
        }
    }
}
