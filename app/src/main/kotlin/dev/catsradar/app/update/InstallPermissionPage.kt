package dev.catsradar.app.update

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/** The system page, for this app alone, where the user allows it to install packages. */
fun installPermissionPage(packageName: String): Intent =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri())
