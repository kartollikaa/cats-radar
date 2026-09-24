package dev.catsradar.app.navigation

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
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
import dev.catsradar.domain.repository.EncounterRepository
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
import kotlin.test.assertEquals
import kotlin.time.Clock

@RunWith(AndroidJUnit4::class)
class RegionsEntryTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a cat tapped in the nav host's own places entry opens that cat above the list`() {
        val koin = startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Context>())
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin
        runBlocking { koin.get<EncounterRepository>().insert(tally("cat-1", Clock.System.now())) }
        val levels = listOf<NavKey>(Counter, Statistics, Regions(), Regions(RegionKind.NO_LOCATION))
        val backStack = BottomNavBackStack(NavBackStack(*levels.toTypedArray()))
        val entries = catsRadarEntries(backStack, PaddingValues(), CameraRequest(), MapFocusRequest())
        compose.setContent { CatsRadarTheme { entries(levels.last()).Content() } }
        val cats = compose.onAllNodes(hasClickAction())
        compose.waitUntil { cats.fetchSemanticsNodes().isNotEmpty() }

        cats.onFirst().performClick()

        assertEquals(levels + EncounterDetail("cat-1"), backStack.toList())
    }
}
