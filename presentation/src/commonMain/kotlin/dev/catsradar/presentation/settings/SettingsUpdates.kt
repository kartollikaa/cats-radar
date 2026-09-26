package dev.catsradar.presentation.settings

import dev.catsradar.domain.about.InstalledApp
import dev.catsradar.domain.platform.InstallPermission
import dev.catsradar.domain.update.UpdateCheck
import dev.catsradar.domain.update.isOlderThan
import dev.catsradar.domain.usecase.CheckForUpdate

/** The part of Settings the screen reads and writes for [SettingsUpdates]. */
internal interface UpdateScreen {
    val update: UpdateState

    fun show(update: UpdateState)

    suspend fun send(effect: SettingsEffect)
}

/** Updating the app from Settings; one per Settings screen, which it keeps the state of the download for. */
class SettingsUpdates(
    private val checkForUpdate: CheckForUpdate,
    private val installed: InstalledApp,
    private val installPermission: InstallPermission,
    private val mapper: UpdateStateMapper,
) {
    // Only a download this screen asked for installs by itself; one found finished waits for a tap.
    private var installWhenDownloaded = false
    private var downloadedPath: String? = null
    private var handledDownloadRun: String? = null

    internal suspend fun handle(intent: SettingsIntent.Update, screen: UpdateScreen) {
        when (intent) {
            SettingsIntent.Update.CheckClicked -> check(screen)
            SettingsIntent.Update.InstallClicked -> installDownloaded(screen)
            SettingsIntent.Update.AllowInstallsClicked -> screen.send(SettingsEffect.OpenInstallPermission)
            SettingsIntent.Update.InstallPermissionReturned -> permissionReturned(screen)
            is SettingsIntent.Update.DownloadProgressed -> if (downloadShowable(intent.version, screen)) {
                screen.show(mapper.downloading(intent.version, intent.fraction))
            }
            is SettingsIntent.Update.DownloadFinished -> downloadFinished(intent, screen)
            is SettingsIntent.Update.DownloadFailed -> downloadFailed(intent.runId, screen)
            is SettingsIntent.Update.InstallFinished -> installFinished(intent.outcome, screen)
        }
    }

    private suspend fun check(screen: UpdateScreen) {
        if (screen.update.action != UpdateAction.Check) return
        screen.show(mapper.checking())
        val result = checkForUpdate()
        screen.show(mapper.map(result))
        if (result is UpdateCheck.Available) {
            installWhenDownloaded = true
            screen.send(SettingsEffect.StartUpdateDownload(result.version.toString(), result.apk))
        }
    }

    private fun downloadShowable(version: String, screen: UpdateScreen): Boolean = installed.isOlderThan(version) &&
        screen.update.status.let { it !is UpdateStatus.ReadyToInstall && it !is UpdateStatus.Installing }

    private suspend fun downloadFinished(finished: SettingsIntent.Update.DownloadFinished, screen: UpdateScreen) {
        if (finished.runId == handledDownloadRun) return
        handledDownloadRun = finished.runId
        if (!installed.isOlderThan(finished.version)) return
        downloadedPath = finished.path
        if (installWhenDownloaded) {
            installWhenDownloaded = false
            install(finished.version, finished.path, screen)
        } else {
            screen.show(mapper.ready(finished.version))
        }
    }

    private fun downloadFailed(runId: String, screen: UpdateScreen) {
        if (runId == handledDownloadRun) return
        handledDownloadRun = runId
        val status = screen.update.status
        if (status !is UpdateStatus.Downloading && status !is UpdateStatus.DownloadStarting) return
        installWhenDownloaded = false
        screen.show(mapper.downloadFailed())
    }

    private suspend fun installDownloaded(screen: UpdateScreen) {
        val action = screen.update.action as? UpdateAction.Install ?: return
        downloadedPath?.let { install(action.version, it, screen) }
    }

    // Asked before every install: without it Android shows a refusal instead of its confirmation.
    private suspend fun install(version: String, path: String, screen: UpdateScreen) {
        if (!installPermission.granted()) {
            screen.show(mapper.needsInstallPermission(version))
            screen.send(SettingsEffect.OpenInstallPermission)
            return
        }
        screen.show(mapper.installing(version))
        screen.send(SettingsEffect.InstallUpdate(path))
    }

    private suspend fun permissionReturned(screen: UpdateScreen) {
        val waiting = screen.update.status as? UpdateStatus.NeedsInstallPermission ?: return
        val path = downloadedPath ?: return
        if (installPermission.granted()) install(waiting.version, path, screen)
    }

    private fun installFinished(outcome: InstallOutcome, screen: UpdateScreen) {
        val version = (screen.update.status as? UpdateStatus.Installing)?.version ?: return
        screen.show(
            when (outcome) {
                InstallOutcome.CANCELLED -> mapper.ready(version)
                else -> mapper.installFailed(version, outcome)
            },
        )
    }
}
