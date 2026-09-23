package dev.catsradar.app.photo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.catsradar.app.MainActivity

internal enum class Launch { FINISH, OPEN_CAMERA, SHOW }

internal object TakePhotoShortcut {

    private const val ACTION = "dev.catsradar.action.TAKE_PHOTO"
    private const val PENDING = "cameraRequestPending"

    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION)
        // A running app takes the request in onNewIntent rather than stacking a second copy of itself.
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Whether [intent], delivered now, asks for a photo; reopened from recents, it does not. */
    fun isRequest(intent: Intent?): Boolean =
        intent?.action == ACTION && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0

    /**
     * What an activity being created should do. A recreated one still holds its own launch intent,
     * so only the request it saved unfinished counts. Once Photo has reached the app's task the
     * launcher's intent no longer matches it, and Android stacks a second copy above the first.
     */
    fun onCreate(intent: Intent?, isTaskRoot: Boolean, isOwnTask: () -> Boolean, savedState: Bundle?): Launch =
        when {
            !isTaskRoot && intent.isLauncherStart() && isOwnTask() -> Launch.FINISH
            savedState != null -> if (savedState.getBoolean(PENDING)) Launch.OPEN_CAMERA else Launch.SHOW
            isRequest(intent) -> Launch.OPEN_CAMERA
            else -> Launch.SHOW
        }

    fun save(outState: Bundle, requestPending: Boolean) {
        outState.putBoolean(PENDING, requestPending)
    }

    private fun Intent?.isLauncherStart(): Boolean =
        this?.action == Intent.ACTION_MAIN && hasCategory(Intent.CATEGORY_LAUNCHER)
}
