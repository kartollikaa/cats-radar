package dev.catsradar.app.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import dev.catsradar.domain.platform.WalkRecordingState
import dev.catsradar.domain.usecase.RecordWalk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Records the walk's route for as long as it carries the walking notification in the foreground. */
class WalkRecordingService : Service(), KoinComponent {

    private val notifier: WalkingNotifier by inject()
    private val recordWalk: RecordWalk by inject()
    private val recordingState: WalkRecordingState by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recording: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra(EXTRA_COUNT, 0) ?: 0
        if (!carryNotification(count)) {
            notifier.show(count, appOnScreen = false)
            stopSelf()
        } else if (recording == null) {
            recordingState.markRecording()
            recording = scope.launch { recordWalk() }
        }
        // Not restarted when killed: started from the background it would get no location access.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        recordingState.markStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Refused when location was revoked, or the app left the screen, before this ran.
    @Suppress("SwallowedException")
    private fun carryNotification(count: Int): Boolean =
        try {
            notifier.carry(this, count)
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }

    companion object {
        const val EXTRA_COUNT = "dev.catsradar.extra.WALK_COUNT"

        fun intent(context: Context, count: Int): Intent =
            Intent(context, WalkRecordingService::class.java).putExtra(EXTRA_COUNT, count)
    }
}
