package dev.catsradar.app.navigation

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NavTransitionTimingTest {

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

    @Before
    fun showTheNavDisplay() {
        compose.setContent {
            CatsRadarNavDisplay(
                backStack = backStack,
                entryProvider = entryProvider {
                    entry<Counter>(metadata = tabRootMetadata()) { Screen(COUNTER) }
                    entry<Encounters>(metadata = tabRootMetadata()) { Screen(ENCOUNTERS) }
                    entry<EncounterDetail> { Screen(DETAIL) }
                },
            )
        }
    }

    @Test
    fun `a tab switch runs no longer than the bound`() {
        val tabSwitch = transitionMs(leaving = COUNTER, entering = ENCOUNTERS) {
            backStack.selectTab(BottomNavTab.ENCOUNTERS)
        }

        assertWithinTheBound(tabSwitch)
    }

    @Test
    fun `opening a detail runs no longer than the bound`() {
        settle { backStack.selectTab(BottomNavTab.ENCOUNTERS) }

        assertWithinTheBound(transitionMs(leaving = ENCOUNTERS, entering = DETAIL) { backStack.push(DETAIL_KEY) })
    }

    @Test
    fun `going back from a detail runs no longer than the bound`() {
        settle {
            backStack.selectTab(BottomNavTab.ENCOUNTERS)
            backStack.push(DETAIL_KEY)
        }

        val back = transitionMs(leaving = DETAIL, entering = ENCOUNTERS) { backStack.popOrNull() }

        assertWithinTheBound(back)
    }

    @Test
    fun `an opened detail comes in from the right`() {
        settle { backStack.selectTab(BottomNavTab.ENCOUNTERS) }

        navigate { backStack.push(DETAIL_KEY) }
        advanceFrames(PICK_UP_FRAMES + INTO_MOTION_FRAMES)

        val left = compose.onNodeWithText(DETAIL).getUnclippedBoundsInRoot().left
        assertTrue(left > 0.dp, "the detail's left edge is $left while it enters")
    }

    @Test
    fun `going back brings the screen underneath in from the left`() {
        settle {
            backStack.selectTab(BottomNavTab.ENCOUNTERS)
            backStack.push(DETAIL_KEY)
        }

        navigate { backStack.popOrNull() }
        advanceFrames(PICK_UP_FRAMES + INTO_MOTION_FRAMES)

        val left = compose.onNodeWithText(ENCOUNTERS).getUnclippedBoundsInRoot().left
        assertTrue(left < 0.dp, "the list's left edge is $left while it returns")
    }

    private fun settle(change: () -> Unit) {
        compose.runOnIdle(change)
        compose.waitForIdle()
    }

    private fun navigate(change: () -> Unit) {
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread(change)
    }

    // One frame at a time with an idle sync between, so the transition starts when it would on a device.
    private fun advanceFrames(count: Int) = repeat(count) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    /** From the frame the entering screen first appears to the frame the leaving one is dropped. */
    private fun transitionMs(leaving: String, entering: String, change: () -> Unit): Long {
        navigate(change)
        var startedAt: Long? = null
        repeat(MAX_FRAMES) {
            advanceFrames(1)
            val now = compose.mainClock.currentTime
            if (startedAt == null && isShown(entering)) startedAt = now
            val start = startedAt
            if (start != null && !isShown(leaving)) return now - start
        }
        error("the transition from '$leaving' to '$entering' did not finish within $MAX_FRAMES frames")
    }

    private fun isShown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun assertWithinTheBound(transitionMs: Long) =
        assertTrue(transitionMs <= FINISHED_BY_MS, "the transition took $transitionMs ms")

    private companion object {
        const val COUNTER = "counter screen"
        const val ENCOUNTERS = "encounters screen"
        const val DETAIL = "detail screen"
        val DETAIL_KEY = EncounterDetail("encounter-1")
        const val BOUND_MS = 300L
        const val FRAME_MS = 16L

        // The motion ends on the first frame at or past the bound; the frame after it drops the screen.
        const val FINISHED_BY_MS = (BOUND_MS + FRAME_MS - 1) / FRAME_MS * FRAME_MS + FRAME_MS
        const val MAX_FRAMES = 120
        const val PICK_UP_FRAMES = 3
        const val INTO_MOTION_FRAMES = 3
    }
}

@Composable
private fun Screen(label: String) {
    Box(Modifier.fillMaxSize()) { Text(label) }
}
