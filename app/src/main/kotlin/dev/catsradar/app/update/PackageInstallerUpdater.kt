package dev.catsradar.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

interface UpdateInstaller {
    /** False when the package could not be handed to Android; its answer arrives through [InstallResults]. */
    suspend fun install(path: String): Boolean
}

class PackageInstallerUpdater(
    private val context: Context,
    private val packageInstaller: PackageInstaller,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdateInstaller {

    @Suppress("SwallowedException") // a package Android was never given is reported as not installed
    override suspend fun install(path: String): Boolean = withContext(ioDispatcher) {
        val apk = File(path)
        if (!apk.isFile) return@withContext false
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val sessionId = packageInstaller.createSession(params)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(statusReceiver(sessionId).intentSender)
            }
            true
        } catch (e: IOException) {
            packageInstaller.abandonSession(sessionId)
            false
        }
    }

    // Mutable, because the installer fills in the status; explicit, which Android requires of a mutable one.
    private fun statusReceiver(sessionId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(context, UpdateInstallReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
