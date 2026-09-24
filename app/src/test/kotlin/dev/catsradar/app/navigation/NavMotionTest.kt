package dev.catsradar.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import dev.catsradar.app.photo.CameraRequest
import dev.catsradar.ui.navigation.BottomNavTab
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavMotionTest {

    private val counter = tabRoot(Counter)
    private val encounters = tabRoot(Encounters)
    private val statistics = tabRoot(Statistics)
    private val detail = detail(EncounterDetail("encounter-1"))
    private val places = detail(Regions())
    private val country = detail(Regions(RegionKind.COUNTRY, countryCode = "ES"))
    private val map = tabRoot(CatsMap)
    private val spot = sheet(MapSpot(setOf("a", "b"), emptySet()))

    @Test
    fun `switching from one tab root to another fades through`() {
        assertEquals(NavMotion.FADE_THROUGH, navMotion(scene(counter, encounters), scene(counter, statistics)))
    }

    @Test
    fun `opening a tab from the Counter root fades through even though the tab sits on it`() {
        assertEquals(NavMotion.FADE_THROUGH, navMotion(scene(counter), scene(counter, encounters)))
    }

    @Test
    fun `going back from a tab to the Counter root fades through`() {
        assertEquals(NavMotion.FADE_THROUGH, navMotion(scene(counter, encounters), scene(counter)))
    }

    @Test
    fun `opening a detail on top of its tab moves forward`() {
        assertEquals(NavMotion.FORWARD, navMotion(scene(counter, encounters), scene(counter, encounters, detail)))
    }

    @Test
    fun `opening a detail on top of another detail moves forward`() {
        val from = scene(counter, statistics, places)
        val to = scene(counter, statistics, places, country)

        assertEquals(NavMotion.FORWARD, navMotion(from, to))
    }

    @Test
    fun `going back from a detail to the screen under it moves backward`() {
        assertEquals(NavMotion.BACKWARD, navMotion(scene(counter, encounters, detail), scene(counter, encounters)))
    }

    @Test
    fun `going back from a detail to the detail under it moves backward`() {
        val from = scene(counter, statistics, places, country)
        val to = scene(counter, statistics, places)

        assertEquals(NavMotion.BACKWARD, navMotion(from, to))
    }

    @Test
    fun `a cat opened from a sheet moves forward from the screen under the sheet`() {
        assertEquals(NavMotion.FORWARD, navMotion(scene(counter, map), scene(counter, map, spot, detail)))
    }

    @Test
    fun `going back from a cat opened from a sheet moves backward to the screen under the sheet`() {
        assertEquals(NavMotion.BACKWARD, navMotion(scene(counter, map, spot, detail), scene(counter, map)))
    }

    @Test
    fun `leaving a detail for another tab fades through`() {
        assertEquals(NavMotion.FADE_THROUGH, navMotion(scene(counter, encounters, detail), scene(counter, statistics)))
    }

    @Test
    fun `leaving a detail for the Counter root fades through although the stack only shrinks`() {
        assertEquals(NavMotion.FADE_THROUGH, navMotion(scene(counter, encounters, detail), scene(counter)))
    }

    @Test
    fun `reselecting a tab from two levels down fades through`() {
        val from = scene(counter, statistics, places, country)
        val to = scene(counter, statistics)

        assertEquals(NavMotion.FADE_THROUGH, navMotion(from, to))
    }

    @Test
    fun `every bottom-bar tab entry the nav host builds fades through from the Counter root`() {
        val backStack = BottomNavBackStack(NavBackStack(Counter))
        val entries = catsRadarEntries(backStack, PaddingValues(), CameraRequest(), MapFocusRequest())
        val counterRoot = scene(entries(Counter))

        BottomNavTab.entries.filter { it != BottomNavTab.COUNTER }.forEach { tab ->
            backStack.selectTab(tab)
            val tabRoot = scene(entries(Counter), entries(backStack.last()))

            assertEquals(NavMotion.FADE_THROUGH, navMotion(counterRoot, tabRoot), "$tab")
        }
    }

    @Test
    fun `the back gesture both shrinks and fades the screen it leaves`() {
        val exit = predictivePopTransition(NavigationEvent.EDGE_LEFT).initialContentExit

        assertTrue(exit.scales(), "shrinks")
        assertTrue(exit.fades(), "fades")
    }

    @Test
    fun `the back gesture fades in the screen it returns to`() {
        assertTrue(predictivePopTransition(NavigationEvent.EDGE_LEFT).targetContentEnter.fades())
    }

    @Test
    fun `the back gesture shrinks toward the edge the finger moves to, or the centre without an edge`() {
        assertEquals(1f, shrinkPivotX(NavigationEvent.EDGE_LEFT))
        assertEquals(0f, shrinkPivotX(NavigationEvent.EDGE_RIGHT))
        assertEquals(0.5f, shrinkPivotX(NavigationEvent.EDGE_NONE))
    }

    // Adding a fade or a scale leaves a transition equal to itself only when it already has its own.
    private fun ExitTransition.fades() = fadeOut() + this == this || this + fadeOut() == this

    private fun ExitTransition.scales() = scaleOut() + this == this || this + scaleOut() == this

    private fun EnterTransition.fades() = fadeIn() + this == this || this + fadeIn() == this

    private fun tabRoot(key: NavKey): NavEntry<NavKey> = NavEntry(key, metadata = tabRootMetadata()) {}

    private fun detail(key: NavKey): NavEntry<NavKey> = NavEntry(key) {}

    private fun sheet(key: NavKey): NavEntry<NavKey> =
        NavEntry(key, metadata = BottomSheetSceneStrategy.bottomSheet()) {}

    private fun scene(vararg stack: NavEntry<NavKey>): Scene<NavKey> = StackTopScene(stack.toList())

    private data class StackTopScene(val stack: List<NavEntry<NavKey>>) : Scene<NavKey> {
        override val key: Any get() = stack.last().contentKey
        override val entries: List<NavEntry<NavKey>> get() = listOf(stack.last())
        override val previousEntries: List<NavEntry<NavKey>> get() = stack.dropLast(1)
        override val content: @Composable () -> Unit = {}
    }
}
