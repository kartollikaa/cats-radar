package dev.catsradar.app.navigation

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.di.dataModule
import dev.catsradar.app.di.domainModule
import dev.catsradar.app.di.presentationModule
import dev.catsradar.app.di.workerModule
import dev.catsradar.app.notification.tally
import dev.catsradar.app.photo.CameraRequest
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.presentation.map.MapIntent
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class RegionsEntryTest {

    private val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a cat tapped in the nav host's own places entry opens that cat above the list`() {
        val koin = startKoin {
            androidContext(context)
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin
        runBlocking { koin.get<EncounterRepository>().insert(tally("cat-1", Instant.parse("2026-09-21T10:00:00Z"))) }
        val levels = listOf<NavKey>(Counter, Statistics, Regions(), Regions(RegionKind.NO_LOCATION))
        val backStack = BottomNavBackStack(NavBackStack(*levels.toTypedArray()))
        val entries = catsRadarEntries(backStack, PaddingValues(), CameraRequest(), MapFocusRequest())
        compose.setContent { CatsRadarTheme { entries(levels.last()).Content() } }
        compose.waitUntil { compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(hasClickAction()).performClick()

        assertEquals(levels + EncounterDetail("cat-1"), backStack.toList())
    }

    @Test
    fun `On the map in a place's cats opens the outing on the Map tab`() {
        val koin = startKoin {
            androidContext(context)
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin
        val located = tally("cat-1", Instant.parse("2026-09-21T10:00:00Z"))
            .copy(lat = 41.39, lon = 2.17, locationSource = LocationSource.CURRENT_FIX)
        runBlocking { koin.get<EncounterRepository>().insert(located) }
        val area = Regions(RegionKind.UNRESOLVED_AREA, areaHash = Geohash.encode(41.39, 2.17, Tuning.AREA_PRECISION))
        val levels = listOf<NavKey>(Counter, Statistics, Regions(), Regions(RegionKind.UNRESOLVED), area)
        val backStack = BottomNavBackStack(NavBackStack(*levels.toTypedArray()))
        val mapFocus = MapFocusRequest()
        val entries = catsRadarEntries(backStack, PaddingValues(), CameraRequest(), mapFocus)
        compose.setContent { CatsRadarTheme { entries(levels.last()).Content() } }
        val onTheMap = hasText(context.getString(R.string.encounters_outing_on_map))
        compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodes(onTheMap).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNode(onTheMap).performClick()
        compose.waitForIdle()

        assertEquals(listOf(Counter, CatsMap), backStack.toList())
        assertEquals(MapIntent.OutingFocused("cat-1"), mapFocus.consume())
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
