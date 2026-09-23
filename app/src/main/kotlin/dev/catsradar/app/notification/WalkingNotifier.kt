package dev.catsradar.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.catsradar.app.MainActivity
import dev.catsradar.app.photo.TakePhotoShortcut
import dev.catsradar.ui.R
import dev.catsradar.app.R as AppR

// Android lets an app lower a channel's importance but never raise it; a raised one needs a new id.
private const val CHANNEL_ID = "walking_lock_screen"
private const val RETIRED_CHANNEL_ID = "walking"
private const val NOTIFICATION_ID = 2

/**
 * The walking notification: one tap logs a cat without unlocking the phone or opening the app.
 *
 * There is no foreground service behind it. An ongoing notification survives the app leaving the
 * foreground on its own, and the action's broadcast starts the process again if it has gone — a
 * service would buy nothing here but permissions.
 */
class WalkingNotifier(private val context: Context) : WalkingNotifications {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        manager.deleteNotificationChannel(RETIRED_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_walking),
                // Below Default a notification counts as silent, and lock screens hide silent ones by default.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { setSound(null, null) },
        )
    }

    override fun show(count: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AppR.drawable.ic_notification_cat)
            .setContentTitle(context.getString(R.string.notification_walking_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notification_walking_count, count, count))
            .setOngoing(true)
            .setSilent(true)
            // API 36.1 and up may promote an ongoing notification to the status-bar chip and the
            // always-on display; below that the same notification posts, unpromoted.
            .setRequestPromotedOngoing(true)
            // What the chip shows. A promoted notification without it gets a chip with only an icon.
            .setShortCriticalText(count.toString())
            // Swiping it away ends the walk. Reposting something the user has just dismissed is
            // what makes people turn Live Updates off for an app.
            .setDeleteIntent(broadcast(WalkingAction.STOP))
            .setContentIntent(openApp())
            // Visible on the lock screen: tallying without unlocking is the whole point.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_input_add,
                context.getString(R.string.notification_walking_tally),
                broadcast(WalkingAction.TALLY),
            )
            .addAction(
                android.R.drawable.ic_menu_camera,
                context.getString(R.string.notification_walking_photo),
                takePhoto(),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.notification_walking_stop),
                broadcast(WalkingAction.STOP),
            )
            .build()
        post(notification)
    }

    override fun clear() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, WalkingActionReceiver::class.java).setAction(action),
        // Immutable: nothing may rewrite where a lock-screen tap ends up.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // Equal to the launcher's intent, so a running app comes forward instead of stacking a second copy.
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun takePhoto(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        TakePhotoShortcut.intent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // Same reason as the import notifier: notify raises without the permission rather than
    // no-opping, and the check has to be inline for Lint to see it.
    private fun post(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(NOTIFICATION_ID, notification)
    }
}

internal object WalkingAction {
    const val TALLY = "dev.catsradar.action.WALKING_TALLY"
    const val STOP = "dev.catsradar.action.WALKING_STOP"
}
