package dev.catsradar.app.navigation

import android.content.ComponentName
import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
    private var sheetHeight by mutableStateOf(SHORT_SHEET)

    @Before
    fun showTheMap() {
        compose.setContent {
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            CatsRadarNavDisplay(
                backStack = backStack,
                entryProvider = entryProvider {
                    entry<Counter>(metadata = tabRootMetadata()) { Screen(COUNTER) }
                    entry<CatsMap>(metadata = tabRootMetadata()) { Screen(MAP) }
                    entry<MapSpot>(metadata = BottomSheetSceneStrategy.bottomSheet()) { Sheet(SHEET, sheetHeight) }
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
    fun `a sheet taller than half the screen opens at its full height`() {
        sheetHeight = TALL_SHEET

        settle { backStack.push(SPOT) }

        val sheetTop = sheetTop()
        assertTrue(sheetTop < screenHeight() / 2, "the sheet's content starts at $sheetTop on a ${screenHeight()} screen")
    }

    @Test
    fun `a sheet shorter than half the screen opens at its own height`() {
        settle { backStack.push(SPOT) }

        val sheetTop = sheetTop()
        assertTrue(sheetTop > screenHeight() / 2, "the sheet's content starts at $sheetTop on a ${screenHeight()} screen")
    }

    @Test
    fun `dragging a sheet part of the way down from its full height closes it rather than stopping half open`() {
        sheetHeight = TALL_SHEET
        settle { backStack.push(SPOT) }
        dragSheet(by = -PART_OF_THE_WAY)

        dragSheet(by = PART_OF_THE_WAY)

        assertEquals(listOf(Counter, CatsMap), backStack.toList())
        assertFalse(isShown(SHEET), "the sheet is still open")
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

    private fun dragSheet(by: Dp) {
        compose.onNodeWithTag(SHEET).performTouchInput {
            val start = Offset(centerX, PART_OF_THE_WAY.toPx())
            swipe(start = start, end = start + Offset(0f, by.toPx()), durationMillis = 1_000)
        }
        compose.waitForIdle()
    }

    private fun sheetTop() = compose.onNodeWithTag(SHEET).getUnclippedBoundsInRoot().top

    private fun screenHeight() = compose.onNodeWithTag(MAP).getUnclippedBoundsInRoot().height

    private fun isShown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val COUNTER = "counter screen"
        const val MAP = "map screen"
        const val SHEET = "spot sheet"
        const val CAT = "cat screen"
        val SHORT_SHEET = 120.dp
        val TALL_SHEET = 2000.dp

        // Past the drag that settles a sheet at its next stop, and short of the half-open one.
        val PART_OF_THE_WAY = 160.dp
        val SPOT = MapSpot(catIds = setOf("a", "b"), coats = emptySet())
        val CAT_KEY = EncounterDetail("a")
    }
}

@Composable
private fun Screen(label: String) {
    Box(Modifier.fillMaxSize().testTag(label)) { Text(label) }
}

@Composable
private fun Sheet(label: String, height: Dp) {
    Box(Modifier.fillMaxWidth().height(height).testTag(label)) { Text(label) }
}
