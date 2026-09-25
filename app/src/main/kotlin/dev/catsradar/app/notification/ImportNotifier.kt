package dev.catsradar.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.catsradar.ui.R

private const val CHANNEL_ID = "import"
private const val NOTIFICATION_ID = 1

/**
 * The progress of an import, for when the user has walked away from the screen that was showing it.
 *
 * Posting is best-effort: a user who has not granted notifications still gets the import, just not
 * the running commentary.
 */
class ImportNotifier(
    private val context: Context,
    private val manager: NotificationManagerCompat,
) {

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_import),
                // Low: an import the user started themselves should not interrupt them.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun showProgress(done: Int, total: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notification_import_title))
            .setContentText(context.getString(R.string.counter_import_progress, done, total))
            .setProgress(total, done, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        post(notification)
    }

    fun clear() {
        manager.cancel(NOTIFICATION_ID)
    }

    // NotificationManagerCompat.notify throws without the permission rather than no-opping, and a
    // refused notification must not fail the import that was posting it. The check is inline
    // because Lint only recognises it here, not behind a helper.
    private fun post(notification: Notification) {
        // POST_NOTIFICATIONS does not exist before API 33; asking about it there answers nothing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(NOTIFICATION_ID, notification)
    }
}
