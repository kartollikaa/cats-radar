package dev.catsradar.app.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat

internal fun importNotifier(context: Context) = ImportNotifier(context, NotificationManagerCompat.from(context))
