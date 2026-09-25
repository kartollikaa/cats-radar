package dev.catsradar.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import dev.catsradar.app.photo.CameraRequest
import dev.catsradar.presentation.regions.RegionRowKey
import dev.catsradar.presentation.regions.RegionsStore
import dev.catsradar.ui.navigation.BottomNavTab
import dev.catsradar.ui.navigation.CatsRadarBottomBar
import dev.catsradar.ui.regions.RegionsScreen
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CatsRadarNavHost(cameraRequest: CameraRequest, modifier: Modifier = Modifier) {
    val backStack = rememberBottomNavBackStack()
    val mapFocus = remember { MapFocusRequest() }
    val screenViews = koinInject<ScreenViewTracker>()
    LaunchedEffect(backStack, screenViews) {
        snapshotFlow { backStack.lastOrNull() }.filterNotNull().collect(screenViews::onTop)
    }
    DisposableEffect(screenViews) { onDispose(screenViews::onHostGone) }
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
                mapFocus.postOuting(id)
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
                mapFocus.postOuting(id)
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
    catEntries(backStack, contentPadding, mapFocus)
}

private fun EntryProviderScope<NavKey>.catEntries(
    backStack: BottomNavBackStack,
    contentPadding: PaddingValues,
    mapFocus: MapFocusRequest,
) {
    entry<EncounterDetail> { key ->
        EncounterDetailDestination(
            key = key,
            contentPadding = contentPadding,
            onNavigateBack = { backStack.popIfOnTop(key) },
            onOpenPhoto = { backStack.push(PhotoViewer(key.id)) },
            onOpenMap = {
                mapFocus.postCat(key.id)
                backStack.selectTab(BottomNavTab.MAP)
            },
        )
    }
    entry<PhotoViewer>(metadata = photoViewerMetadata()) { key ->
        PhotoViewerDestination(key = key, onClose = { backStack.popIfOnTop(key) })
    }
}

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
