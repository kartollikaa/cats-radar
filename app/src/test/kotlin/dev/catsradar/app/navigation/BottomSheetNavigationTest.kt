package dev.catsradar.app.navigation

import android.content.ComponentName
import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.ui.navigation.BottomNavTab
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BottomSheetNavigationTest {

    private val compose = createComposeRule()

    // ui-test-manifest reaches a unit test only through debugImplementation, which would ship its activity in the APK.
    private val componentActivityRegistered = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager)
                .addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(componentActivityRegistered).around(compose)

    private val backStack = BottomNavBackStack(NavBackStack<NavKey>(Counter))
    private lateinit var backDispatcher: OnBackPressedDispatcher

    @Before
    fun showTheMap() {
        compose.setContent {
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            CatsRadarNavDisplay(
                backStack = backStack,
                entryProvider = entryProvider {
                    entry<Counter>(metadata = tabRootMetadata()) { Screen(COUNTER) }
                    entry<CatsMap>(metadata = tabRootMetadata()) { Screen(MAP) }
                    entry<MapSpot>(metadata = BottomSheetSceneStrategy.bottomSheet()) { Text(SHEET) }
                    entry<EncounterDetail> { Screen(CAT) }
                },
            )
        }
        settle { backStack.selectTab(BottomNavTab.MAP) }
    }

    @Test
    fun `a sheet entry opens over the screen under it`() {
        settle { backStack.push(SPOT) }

        assertTrue(isShown(SHEET), "the sheet is shown")
        assertTrue(isShown(MAP), "the map is shown under it")
    }

    @Test
    fun `the back gesture from a cat opened from a sheet previews the screen under the sheet, not the sheet`() {
        openACatFromTheSheet()

        startBackGesture()

        assertTrue(isShown(MAP), "the map is drawn under the cat")
        assertFalse(isShown(SHEET), "the sheet is drawn during the gesture")
    }

    @Test
    fun `completing the gesture lands on the sheet over its screen`() {
        openACatFromTheSheet()
        startBackGesture()

        settle { backDispatcher.onBackPressed() }

        assertEquals(listOf(Counter, CatsMap, SPOT), backStack.toList())
        assertTrue(isShown(SHEET), "the sheet is open again")
        assertTrue(isShown(MAP), "the map is under it")
    }

    @Test
    fun `a cancelled gesture leaves the cat on top without the sheet`() {
        openACatFromTheSheet()
        startBackGesture()

        settle { backDispatcher.dispatchOnBackCancelled() }

        assertEquals(listOf(Counter, CatsMap, SPOT, CAT_KEY), backStack.toList())
        assertTrue(isShown(CAT), "the cat is shown")
        assertFalse(isShown(SHEET), "the sheet is drawn over the cat")
    }

    private fun openACatFromTheSheet() {
        settle { backStack.push(SPOT) }
        settle { backStack.push(CAT_KEY) }
        assertFalse(isShown(SHEET), "the sheet stays over the cat")
        assertFalse(isShown(MAP), "the map stays drawn under the cat")
    }

    private fun startBackGesture() = settle {
        backDispatcher.dispatchOnBackStarted(backEvent(progress = 0f))
        backDispatcher.dispatchOnBackProgressed(backEvent(progress = 0.5f))
    }

    private fun backEvent(progress: Float) =
        BackEventCompat(touchX = 0f, touchY = 0f, progress = progress, swipeEdge = BackEventCompat.EDGE_LEFT)

    private fun settle(change: () -> Unit) {
        compose.runOnUiThread(change)
        compose.waitForIdle()
    }

    private fun isShown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val COUNTER = "counter screen"
        const val MAP = "map screen"
        const val SHEET = "spot sheet"
        const val CAT = "cat screen"
        val SPOT = MapSpot(catIds = setOf("a", "b"), coats = emptySet())
        val CAT_KEY = EncounterDetail("a")
    }
}

@Composable
private fun Screen(label: String) {
    Box(Modifier.fillMaxSize()) { Text(label) }
}
