package dev.catsradar.app.photo

import android.content.Context
import android.content.Intent
import dev.catsradar.app.MainActivity

internal object TakePhotoShortcut {

    private const val ACTION = "dev.catsradar.action.TAKE_PHOTO"

    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION)
        // A running app takes the request in onNewIntent rather than stacking a second copy of itself.
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    // Reopened from recents, an activity is handed the intent it was first started with.
    fun isRequest(intent: Intent?): Boolean =
        intent?.action == ACTION && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
}
