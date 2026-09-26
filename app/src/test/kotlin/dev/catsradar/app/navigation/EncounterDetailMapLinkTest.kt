package dev.catsradar.app.navigation

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.hasText
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
import dev.catsradar.domain.model.Encounter
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
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class EncounterDetailMapLinkTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()
    private val mapFocus = MapFocusRequest()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a tap on a cat's coordinates switches to the map tab and asks it for that cat`() {
        val backStack = show(listOf(Counter, Encounters, EncounterDetail(ID)), cat = located())
        awaitTheDatabase { mapLink().fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(opensTheMap() and hasText(COORDINATES)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(Counter, CatsMap), backStack.toList())
        assertEquals(MapIntent.CatRequested(ID), mapFocus.consume())
    }

    @Test
    fun `a cat opened from the map goes back to the map tab, asking it for that cat`() {
        val backStack = show(listOf(Counter, CatsMap, EncounterDetail(ID)), cat = located())
        awaitTheDatabase { mapLink().fetchSemanticsNodes().isNotEmpty() }

        mapLink().onFirst().performClick()
        compose.waitForIdle()

        assertEquals(listOf(Counter, CatsMap), backStack.toList())
        assertEquals(MapIntent.CatRequested(ID), mapFocus.consume())
    }

    @Test
    fun `a cat without coordinates offers no map`() {
        show(listOf(Counter, Encounters, EncounterDetail(ID)), cat = tally(ID, OCCURRED))
        val noLocation = hasText(context.getString(R.string.location_none))
        awaitTheDatabase { compose.onAllNodes(noLocation).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(noLocation).assert(hasNoClickAction())
        assertTrue(mapLink().fetchSemanticsNodes().isEmpty(), "the Where section opens a map")
    }

    @Test
    fun `the map tab takes the cat it was asked for as it opens`() {
        mapFocus.postCat(ID)
        assertEquals(MapIntent.CatRequested(ID), mapFocus.pending)

        show(listOf(Counter, CatsMap), cat = tally(ID, OCCURRED))

        awaitTheDatabase { mapFocus.pending == null }
    }

    // Robolectric's paused main looper delivers the database's answer only when idled; a still screen never idles it.
    private fun awaitTheDatabase(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun show(keys: List<NavKey>, cat: Encounter): BottomNavBackStack {
        val koin = startKoin {
            androidContext(context)
            modules(domainModule, dataModule, presentationModule, workerModule)
        }.koin
        runBlocking { koin.get<EncounterRepository>().insert(cat) }
        val backStack = BottomNavBackStack(NavBackStack(*keys.toTypedArray()))
        val entries = catsRadarEntries(backStack, PaddingValues(), CameraRequest(), mapFocus)
        compose.setContent {
            // A located cat's map needs MapLibre's native runtime, which the JVM cannot start.
            CompositionLocalProvider(LocalInspectionMode provides (cat.lat != null)) {
                CatsRadarTheme { entries(keys.last()).Content() }
            }
        }
        return backStack
    }

    private fun located() =
        tally(ID, OCCURRED).copy(lat = 41.39, lon = 2.17, locationSource = LocationSource.CURRENT_FIX)

    private fun opensTheMap() = SemanticsMatcher("opens the map") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == context.getString(R.string.detail_show_on_map)
    }

    private fun mapLink() = compose.onAllNodes(opensTheMap())

    private companion object {
        const val ID = "cat-1"
        const val COORDINATES = "41.39000, 2.17000"
        const val LOAD_TIMEOUT_MS = 5_000L
        val OCCURRED = Instant.parse("2026-09-21T10:00:00Z")
    }
}
