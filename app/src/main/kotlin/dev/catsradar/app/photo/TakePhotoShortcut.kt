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

    /**
     * Whether [intent] asks for a photo now. A [recreated] activity, or one reopened from recents,
     * still holds the intent that last reached its task, and that request was carried out already.
     */
    fun isRequest(intent: Intent?, recreated: Boolean): Boolean =
        !recreated && intent?.action == ACTION && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0

    /**
     * Whether a launcher start would stack a second copy of the app on its own task: once this
     * shortcut has reached a task, the launcher's intent no longer matches it.
     */
    fun isSecondLauncherCopy(intent: Intent?, isTaskRoot: Boolean): Boolean =
        !isTaskRoot && intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
}
