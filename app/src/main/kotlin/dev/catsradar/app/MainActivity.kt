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

private const val CAMERA_REQUEST_PENDING = "cameraRequestPending"

class MainActivity : ComponentActivity() {

    private val cameraRequest = CameraRequest()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (TakePhotoShortcut.isSecondLauncherCopy(intent, isTaskRoot)) {
            finish()
            return
        }
        enableEdgeToEdge()
        val restoredRequest = savedInstanceState?.getBoolean(CAMERA_REQUEST_PENDING) == true
        if (restoredRequest || TakePhotoShortcut.isRequest(intent, recreated = savedInstanceState != null)) {
            cameraRequest.post()
        }
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
        if (TakePhotoShortcut.isRequest(intent, recreated = false)) cameraRequest.post()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(CAMERA_REQUEST_PENDING, cameraRequest.isPending)
    }
}
