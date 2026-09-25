package dev.catsradar.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dev.catsradar.presentation.settings.InstallOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface InstallStart {
    /** Handed to Android, whose answer arrives through [InstallResults]. */
    data object Committed : InstallStart
    data class Refused(val outcome: InstallOutcome) : InstallStart
}

interface UpdateInstaller {
    suspend fun install(path: String): InstallStart
}

/** What a package file says it is; null when Android cannot read it as a package. */
data class PackageArchive(val packageName: String, val versionCode: Long)

class PackageInstallerUpdater(
    private val context: Context,
    private val packageInstaller: PackageInstaller,
    private val installedVersionCode: Long,
    private val readArchive: (String) -> PackageArchive?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdateInstaller {

    // Android's installer throws both when it cannot take a session: no room, or no installer service.
    @Suppress("SwallowedException") // a package Android was never given is reported as not installed
    override suspend fun install(path: String): InstallStart = withContext(ioDispatcher) {
        val apk = File(path)
        refusal(apk)?.let { return@withContext InstallStart.Refused(it) }
        var sessionId: Int? = null
        try {
            sessionId = packageInstaller.createSession(sessionParams(apk.length()))
            packageInstaller.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(statusReceiver(sessionId).intentSender)
            }
            InstallStart.Committed
        } catch (e: IOException) {
            sessionId?.let(packageInstaller::abandonSession)
            InstallStart.Refused(InstallOutcome.FAILED)
        } catch (e: SecurityException) {
            sessionId?.let(packageInstaller::abandonSession)
            InstallStart.Refused(InstallOutcome.FAILED)
        }
    }

    // Checked here so that the user reads why, rather than meeting Android's generic refusal.
    private fun refusal(apk: File): InstallOutcome? {
        if (!apk.isFile) return InstallOutcome.MISSING_PACKAGE
        val archive = readArchive(apk.absolutePath)
        return when {
            archive == null -> InstallOutcome.FAILED
            archive.packageName != context.packageName -> InstallOutcome.NOT_THIS_APP
            archive.versionCode <= installedVersionCode -> InstallOutcome.NOT_NEWER
            else -> null
        }
    }

    private fun sessionParams(size: Long) =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(size)
        }

    // Mutable, because the installer fills in the status; explicit, which Android requires of a mutable one.
    private fun statusReceiver(sessionId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(context, UpdateInstallReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
