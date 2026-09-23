package dev.catsradar.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import org.junit.Test
import kotlin.test.assertEquals

class NavMotionTest {

    private val counter = tabRoot(Counter)
    private val encounters = tabRoot(Encounters)
    private val statistics = tabRoot(Statistics)
    private val detail = detail(EncounterDetail("encounter-1"))
    private val places = detail(Regions())
    private val country = detail(Regions(RegionKind.COUNTRY, countryCode = "ES"))

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

    private fun tabRoot(key: NavKey): NavEntry<NavKey> = NavEntry(key, metadata = tabRootMetadata()) {}

    private fun detail(key: NavKey): NavEntry<NavKey> = NavEntry(key) {}

    private fun scene(vararg stack: NavEntry<NavKey>): Scene<NavKey> = StackTopScene(stack.toList())

    private data class StackTopScene(val stack: List<NavEntry<NavKey>>) : Scene<NavKey> {
        override val key: Any get() = stack.last().contentKey
        override val entries: List<NavEntry<NavKey>> get() = listOf(stack.last())
        override val previousEntries: List<NavEntry<NavKey>> get() = stack.dropLast(1)
        override val content: @Composable () -> Unit = {}
    }
}
