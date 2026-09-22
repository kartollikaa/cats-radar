package dev.catsradar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.catsradar.app.navigation.CatsRadarNavHost
import dev.catsradar.app.photo.CameraRequest
import dev.catsradar.app.photo.TakePhotoShortcut
import dev.catsradar.ui.theme.CatsRadarTheme

class MainActivity : ComponentActivity() {

    private val cameraRequest = CameraRequest()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity still holds its launch intent; that request was carried out already.
        if (savedInstanceState == null && TakePhotoShortcut.isRequest(intent)) cameraRequest.post()
        setContent {
            CatsRadarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CatsRadarNavHost(cameraRequest = cameraRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (TakePhotoShortcut.isRequest(intent)) cameraRequest.post()
    }
}
