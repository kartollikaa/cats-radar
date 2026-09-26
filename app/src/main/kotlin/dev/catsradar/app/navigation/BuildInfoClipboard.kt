package dev.catsradar.app.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import dev.catsradar.ui.R

internal fun copyBuildInfo(
    clipboard: ClipboardManager,
    context: Context,
    report: String,
    sdkInt: Int = Build.VERSION.SDK_INT,
) {
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_about_clip_label), report))
    // Android 13 and later show their own confirmation of every copy; a toast there would say it twice.
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.settings_about_copied, Toast.LENGTH_SHORT).show()
    }
}
