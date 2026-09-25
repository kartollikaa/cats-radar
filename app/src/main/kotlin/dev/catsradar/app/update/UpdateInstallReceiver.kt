package dev.catsradar.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dev.catsradar.presentation.settings.InstallOutcome
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Where Android reports on an update's install session. */
class UpdateInstallReceiver : BroadcastReceiver(), KoinComponent {

    private val results: InstallResults by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            // The system's own confirmation screen; the session waits until the user answers there.
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            // Android replaces the app and ends its process; nothing is left to tell.
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> results.post(InstallOutcome.CANCELLED)
            else -> results.post(InstallOutcome.FAILED)
        }
    }
}
