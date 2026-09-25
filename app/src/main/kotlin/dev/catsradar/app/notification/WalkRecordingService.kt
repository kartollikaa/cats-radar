package dev.catsradar.app.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
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
import kotlin.time.Instant

/** Records the walk's route for as long as it carries the walking notification in the foreground. */
class WalkRecordingService : Service(), KoinComponent {

    private val notifier: WalkingNotifier by inject()
    private val recordWalk: RecordWalk by inject()
    private val recordingState: WalkRecordingState by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recording: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra(EXTRA_COUNT, 0) ?: 0
        val startedAt = intent?.takeIf { it.hasExtra(EXTRA_STARTED_AT) }
            ?.let { Instant.fromEpochMilliseconds(it.getLongExtra(EXTRA_STARTED_AT, 0)) }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        } else if (!carryNotification(count, startedAt)) {
            notifier.show(count, startedAt, appOnScreen = false)
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

    @Suppress("SwallowedException") // refused when the app left the screen, or location went, before this ran
    private fun carryNotification(count: Int, startedAt: Instant?): Boolean =
        try {
            notifier.carry(this, count, startedAt)
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }

    companion object {
        const val EXTRA_COUNT = "dev.catsradar.extra.WALK_COUNT"
        const val EXTRA_STARTED_AT = "dev.catsradar.extra.WALK_STARTED_AT"
        const val ACTION_STOP = "dev.catsradar.action.STOP_RECORDING"

        fun intent(context: Context, count: Int, startedAt: Instant?): Intent =
            Intent(context, WalkRecordingService::class.java)
                .putExtra(EXTRA_COUNT, count)
                .apply { if (startedAt != null) putExtra(EXTRA_STARTED_AT, startedAt.toEpochMilliseconds()) }

        fun stopIntent(context: Context): Intent =
            Intent(context, WalkRecordingService::class.java).setAction(ACTION_STOP)
    }
}

/** Starts and stops [WalkRecordingService] on the walking notification's behalf. */
internal class WalkRecordingControl(private val context: Context) {
    @Volatile
    private var started = false

    @Suppress("SwallowedException") // refused when the app left the screen before the request reached the system
    fun start(count: Int, startedAt: Instant?): Boolean =
        try {
            ContextCompat.startForegroundService(context, WalkRecordingService.intent(context, count, startedAt))
            started = true
            true
        } catch (e: IllegalStateException) {
            false
        }

    // Queued behind a start still on its way: a service stopped from outside before it has gone
    // foreground crashes the app.
    @Suppress("SwallowedException") // refused only when no service is running, so there is nothing to stop
    fun stop() {
        if (!started) return
        started = false
        try {
            context.startService(WalkRecordingService.stopIntent(context))
        } catch (e: IllegalStateException) {
            Unit
        }
    }
}
