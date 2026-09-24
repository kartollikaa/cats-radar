package dev.catsradar.app.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.ui.navigation.BottomNavTab
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PhotoViewerNavigationTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val backStack = BottomNavBackStack(NavBackStack<NavKey>(Counter))

    @Before
    fun openACat() {
        compose.setContent {
            CatsRadarNavDisplay(
                backStack = backStack,
                entryProvider = entryProvider {
                    entry<Counter>(metadata = tabRootMetadata()) { Text(COUNTER) }
                    entry<Encounters>(metadata = tabRootMetadata()) { Text(LIST) }
                    entry<EncounterDetail> { Text(CAT) }
                    entry<PhotoViewer>(metadata = photoViewerMetadata()) { Text(VIEWER) }
                },
            )
        }
        settle { backStack.selectTab(BottomNavTab.ENCOUNTERS) }
        settle { backStack.push(CAT_KEY) }
    }

    @Test
    fun `the viewer opens in a window of its own over the cat`() {
        settle { backStack.push(VIEWER_KEY) }

        assertTrue(inDialog(VIEWER), "the viewer is drawn in a dialog window")
        assertFalse(inDialog(CAT), "the cat is drawn in the dialog")
        assertTrue(isShown(CAT), "the cat stays drawn under the viewer")
    }

    @Test
    fun `back from the viewer uncovers the cat as it was`() {
        settle { backStack.push(VIEWER_KEY) }

        settle { backStack.popOrNull() }

        assertEquals(listOf(Counter, Encounters, CAT_KEY), backStack.toList())
        assertFalse(isShown(VIEWER), "the viewer is gone")
        assertTrue(isShown(CAT), "the cat is shown")
    }

    private fun settle(change: () -> Unit) {
        compose.runOnUiThread(change)
        compose.waitForIdle()
    }

    private fun isShown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun inDialog(text: String) =
        compose.onAllNodes(hasText(text) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val COUNTER = "counter screen"
        const val LIST = "list screen"
        const val CAT = "cat screen"
        const val VIEWER = "viewer screen"
        val CAT_KEY = EncounterDetail("a")
        val VIEWER_KEY = PhotoViewer("a")
    }
}
