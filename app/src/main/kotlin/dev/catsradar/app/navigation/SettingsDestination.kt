package dev.catsradar.app.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.app.worker.BackupScheduler
import dev.catsradar.app.worker.toSettingsIntent
import dev.catsradar.presentation.settings.SettingsEffect
import dev.catsradar.presentation.settings.SettingsIntent
import dev.catsradar.presentation.settings.SettingsStore
import dev.catsradar.ui.settings.SettingsScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun SettingsDestination(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val store = koinViewModel<SettingsStore>()
    val state by store.state.collectAsStateWithLifecycle()
    val backupScheduler = koinInject<BackupScheduler>()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current.nativeClipboard
    val exportLauncher = rememberLauncherForActivityResult(CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        store.dispatch(SettingsIntent.Backup.ExportTargetChosen(uri?.toString()))
    }
    val importLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        store.dispatch(SettingsIntent.Backup.ImportSourceChosen(uri?.toString()))
    }
    // The worker outlives this screen, so its state is read back rather than remembered.
    LaunchedEffect(store, backupScheduler) {
        backupScheduler.observe().collect { info -> info?.toSettingsIntent()?.let(store::dispatch) }
    }
    LaunchedEffect(store, backupScheduler, exportLauncher, importLauncher, clipboard) {
        store.effects.collect { effect ->
            when (effect) {
                SettingsEffect.PickExportTarget -> exportLauncher.launch(defaultBackupName())
                SettingsEffect.PickImportSource -> importLauncher.launch(arrayOf(BACKUP_MIME_TYPE))
                is SettingsEffect.StartExport -> backupScheduler.export(effect.target)
                is SettingsEffect.StartImport -> backupScheduler.import(effect.source)
                is SettingsEffect.CopyBuildInfo -> copyBuildInfo(clipboard, context, effect.report)
            }
        }
    }
    SettingsScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onSaveOriginalsChange = { store.dispatch(SettingsIntent.SaveOriginalsToggled(it)) },
        onEncountersGridChange = { store.dispatch(SettingsIntent.EncountersGridToggled(it)) },
        onExportClick = { store.dispatch(SettingsIntent.Backup.ExportRequested) },
        onImportClick = { store.dispatch(SettingsIntent.Backup.ImportRequested) },
        onBackupOutcomeDismiss = { store.dispatch(SettingsIntent.Backup.OutcomeDismissed) },
        onCopyBuildInfoClick = { store.dispatch(SettingsIntent.BuildInfoCopyClicked) },
    )
}

private const val BACKUP_MIME_TYPE = "application/zip"

// The picker offers this as the name; the date in it is what stops a second export silently
// offering to overwrite the first.
private fun defaultBackupName(): String =
    "cats-radar-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".zip"
