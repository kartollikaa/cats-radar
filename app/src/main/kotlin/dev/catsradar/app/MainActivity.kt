package dev.catsradar.app

import android.app.ActivityManager
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
import dev.catsradar.app.photo.CaptureTarget
import dev.catsradar.app.photo.Launch
import dev.catsradar.app.photo.TakePhotoShortcut
import dev.catsradar.ui.theme.CatsRadarTheme

class MainActivity : ComponentActivity() {

    private val cameraRequest = CameraRequest()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = TakePhotoShortcut.onCreate(
            intent = intent,
            isTaskRoot = isTaskRoot,
            isOwnTask = ::isOwnTask,
            savedState = savedInstanceState,
        )
        when (launch) {
            Launch.FINISH -> {
                finish()
                return
            }
            Launch.OPEN_CAMERA -> cameraRequest.post()
            Launch.SHOW -> Unit
        }
        // A fresh task has no camera answer on its way, so whatever is left there nothing will read.
        if (savedInstanceState == null && isTaskRoot) CaptureTarget.clear(this)
        enableEdgeToEdge()
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

    private fun isOwnTask(): Boolean =
        getSystemService(ActivityManager::class.java).appTasks.any { it.taskInfo?.taskId == taskId }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        TakePhotoShortcut.save(outState, requestPending = cameraRequest.isPending)
    }
}
