package dev.catsradar.app.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.catsradar.app.permission.rememberWalkingModeRequest
import dev.catsradar.app.photo.CameraRequest
import dev.catsradar.app.worker.BackupScheduler
import dev.catsradar.app.worker.toSettingsIntent
import dev.catsradar.presentation.regions.RegionRowKey
import dev.catsradar.presentation.regions.RegionsStore
import dev.catsradar.presentation.settings.SettingsEffect
import dev.catsradar.presentation.settings.SettingsIntent
import dev.catsradar.presentation.settings.SettingsStore
import dev.catsradar.ui.navigation.BottomNavTab
import dev.catsradar.ui.navigation.CatsRadarBottomBar
import dev.catsradar.ui.regions.RegionsScreen
import dev.catsradar.ui.settings.SettingsScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CatsRadarNavHost(cameraRequest: CameraRequest, modifier: Modifier = Modifier) {
    val backStack = rememberBottomNavBackStack()
    val mapFocus = remember { MapFocusRequest() }
    LaunchedEffect(cameraRequest.isPending) {
        if (cameraRequest.isPending) backStack.selectTab(BottomNavTab.COUNTER)
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            CatsRadarBottomBar(
                selectedTab = backStack.selectedTab,
                onTabSelect = { tab -> backStack.selectTab(tab) },
            )
        },
    ) { innerPadding ->
        CatsRadarNavDisplay(
            backStack = backStack,
            entryProvider = catsRadarEntries(backStack, innerPadding, cameraRequest, mapFocus),
        )
    }
}

@Composable
internal fun CatsRadarNavDisplay(
    backStack: BottomNavBackStack,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val sheets = remember(backStack) { BottomSheetSceneStrategy(backStack) }
    val dialogs = remember { DialogSceneStrategy<NavKey>() }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.popOrNull() },
        sceneStrategies = listOf(sheets, dialogs),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { navTransition(density) },
        popTransitionSpec = { navTransition(density) },
        predictivePopTransitionSpec = { swipeEdge -> predictivePopTransition(swipeEdge) },
        entryProvider = entryProvider,
    )
}

internal fun catsRadarEntries(
    backStack: BottomNavBackStack,
    contentPadding: PaddingValues,
    cameraRequest: CameraRequest,
    mapFocus: MapFocusRequest,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<Counter>(metadata = tabRootMetadata()) {
        CounterDestination(contentPadding = contentPadding, cameraRequest = cameraRequest)
    }
    entry<Encounters>(metadata = tabRootMetadata()) {
        EncountersDestination(
            contentPadding = contentPadding,
            onOpenEncounter = { id -> backStack.push(EncounterDetail(id)) },
            onOutingMapClick = { id ->
                mapFocus.post(id)
                backStack.selectTab(BottomNavTab.MAP)
            },
        )
    }
    entry<CatsMap>(metadata = tabRootMetadata()) {
        MapDestination(
            contentPadding = contentPadding,
            focusRequest = mapFocus,
            onOpenCat = { id -> backStack.push(EncounterDetail(id)) },
            onOpenSpot = { spot -> backStack.push(spot) },
        )
    }
    entry<MapSpot>(metadata = BottomSheetSceneStrategy.bottomSheet()) { key ->
        MapSpotDestination(
            key = key,
            onOpenCat = { id -> backStack.push(EncounterDetail(id)) },
            onFocusOuting = { id ->
                mapFocus.post(id)
                backStack.popIfOnTop(key)
            },
            onClose = { backStack.popIfOnTop(key) },
        )
    }
    entry<Statistics>(metadata = tabRootMetadata()) {
        StatisticsDestination(
            contentPadding = contentPadding,
            onPlacesClick = { backStack.push(Regions()) },
        )
    }
    entry<Settings>(metadata = tabRootMetadata()) { SettingsDestination(contentPadding = contentPadding) }
    entry<Regions> { key ->
        RegionsDestination(
            key = key,
            contentPadding = contentPadding,
            onRegionClick = { row -> backStack.push(row.toNavKey()) },
            onEncounterClick = { id -> backStack.push(EncounterDetail(id)) },
        )
    }
    catEntries(backStack, contentPadding)
}

private fun EntryProviderScope<NavKey>.catEntries(backStack: BottomNavBackStack, contentPadding: PaddingValues) {
    entry<EncounterDetail> { key ->
        EncounterDetailDestination(
            key = key,
            contentPadding = contentPadding,
            onNavigateBack = { backStack.popOrNull() },
            onOpenPhoto = { backStack.push(PhotoViewer(key.id)) },
        )
    }
    entry<PhotoViewer>(metadata = photoViewerMetadata()) { key ->
        PhotoViewerDestination(key = key, onClose = { backStack.popIfOnTop(key) })
    }
}

@Composable
private fun SettingsDestination(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val store = koinViewModel<SettingsStore>()
    val state by store.state.collectAsStateWithLifecycle()
    val backupScheduler = koinInject<BackupScheduler>()
    val onWalkingModeChange = rememberWalkingModeRequest { enabled ->
        store.dispatch(SettingsIntent.WalkingModeToggled(enabled))
    }
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
    LaunchedEffect(store, backupScheduler, exportLauncher, importLauncher) {
        store.effects.collect { effect ->
            when (effect) {
                SettingsEffect.PickExportTarget -> exportLauncher.launch(defaultBackupName())
                SettingsEffect.PickImportSource -> importLauncher.launch(arrayOf(BACKUP_MIME_TYPE))
                is SettingsEffect.StartExport -> backupScheduler.export(effect.target)
                is SettingsEffect.StartImport -> backupScheduler.import(effect.source)
            }
        }
    }
    SettingsScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onSaveOriginalsChange = { store.dispatch(SettingsIntent.SaveOriginalsToggled(it)) },
        onWalkingModeChange = onWalkingModeChange,
        onEncountersGridChange = { store.dispatch(SettingsIntent.EncountersGridToggled(it)) },
        onExportClick = { store.dispatch(SettingsIntent.Backup.ExportRequested) },
        onImportClick = { store.dispatch(SettingsIntent.Backup.ImportRequested) },
        onBackupOutcomeDismiss = { store.dispatch(SettingsIntent.Backup.OutcomeDismissed) },
    )
}

private const val BACKUP_MIME_TYPE = "application/zip"

// The picker offers this as the name; the date in it is what stops a second export silently
// offering to overwrite the first.
private fun defaultBackupName(): String =
    "cats-radar-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".zip"

@Composable
private fun RegionsDestination(
    key: Regions,
    contentPadding: PaddingValues,
    onRegionClick: (RegionRowKey) -> Unit,
    onEncounterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = koinViewModel<RegionsStore> { parametersOf(key.toRegionKey()) }
    val state by store.state.collectAsStateWithLifecycle()
    RegionsScreen(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        onRegionClick = onRegionClick,
        onEncounterClick = onEncounterClick,
    )
}
