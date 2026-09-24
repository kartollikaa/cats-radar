package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.catsradar.ui.navigation.BottomNavTab
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomNavigationTest {

    private fun newStack(vararg initial: NavKey): BottomNavBackStack = BottomNavBackStack(NavBackStack(*initial))

    @Test
    fun `the Counter key maps to the Counter tab and any other key maps to Encounters`() {
        assertEquals(BottomNavTab.COUNTER, newStack(Counter).selectedTab)
        assertEquals(BottomNavTab.ENCOUNTERS, newStack(Counter, Encounters).selectedTab)
    }

    @Test
    fun `every tab puts its own screen on the stack and reads back as itself`() {
        val backStack = newStack(Counter)

        val screens = BottomNavTab.entries.associateWith { tab ->
            backStack.selectTab(tab)
            assertEquals(tab, backStack.selectedTab)
            backStack.last()
        }

        assertEquals(
            mapOf(
                BottomNavTab.COUNTER to Counter,
                BottomNavTab.ENCOUNTERS to Encounters,
                BottomNavTab.MAP to CatsMap,
                BottomNavTab.STATISTICS to Statistics,
                BottomNavTab.SETTINGS to Settings,
            ),
            screens,
        )
    }

    @Test
    fun `a cat opened from the map sits above the Map tab, and back returns to the map`() {
        val backStack = newStack(Counter)
        backStack.selectTab(BottomNavTab.MAP)

        backStack.push(EncounterDetail("cat"))

        assertEquals(listOf(Counter, CatsMap, EncounterDetail("cat")), backStack.toList())
        assertEquals(BottomNavTab.MAP, backStack.selectedTab)
        assertTrue(backStack.popOrNull())
        assertEquals(listOf<NavKey>(Counter, CatsMap), backStack.toList())
    }

    @Test
    fun `a cat opened from an area's list sits above the area, and back returns to the area`() {
        val area = Regions(RegionKind.CITY_AREA, countryCode = "ES", city = "Barcelona", areaHash = "sp3e3")
        val levels = listOf<NavKey>(Counter, Statistics, Regions(), Regions(RegionKind.COUNTRY, countryCode = "ES"), area)
        val backStack = newStack(*levels.toTypedArray())

        backStack.push(EncounterDetail("cat"))

        assertEquals(levels + EncounterDetail("cat"), backStack.toList())
        assertEquals(BottomNavTab.STATISTICS, backStack.selectedTab)
        assertTrue(backStack.popOrNull())
        assertEquals(levels, backStack.toList())
    }

    @Test
    fun `popping a sheet by its key pops it only while it is on top`() {
        val spot = MapSpot(setOf("a", "b"), emptySet())
        val backStack = newStack(Counter, CatsMap, spot, EncounterDetail("a"))

        backStack.popIfOnTop(spot)
        assertEquals(listOf(Counter, CatsMap, spot, EncounterDetail("a")), backStack.toList())

        backStack.popOrNull()
        backStack.popIfOnTop(spot)
        assertEquals(listOf<NavKey>(Counter, CatsMap), backStack.toList())

        backStack.popIfOnTop(spot)
        assertEquals(listOf<NavKey>(Counter, CatsMap), backStack.toList())
    }

    @Test
    fun `selecting Encounters from the Counter root pushes it once`() {
        val backStack = newStack(Counter)

        backStack.selectTab(BottomNavTab.ENCOUNTERS)

        assertEquals(listOf<NavKey>(Counter, Encounters), backStack.toList())
    }

    @Test
    fun `selecting Counter while on Encounters returns to the single-entry root stack`() {
        val backStack = newStack(Counter, Encounters)

        backStack.selectTab(BottomNavTab.COUNTER)

        assertEquals(listOf<NavKey>(Counter), backStack.toList())
    }

    @Test
    fun `selecting the already-active tab again never leaves a duplicate key on the stack`() {
        val backStack = newStack(Counter)

        backStack.selectTab(BottomNavTab.ENCOUNTERS)
        backStack.selectTab(BottomNavTab.ENCOUNTERS)

        assertEquals(listOf<NavKey>(Counter, Encounters), backStack.toList())
        assertEquals(1, backStack.count { it == Encounters })
    }

    @Test
    fun `every tab selection in a mixed sequence leaves each key on the stack at most once`() {
        val backStack = newStack(Counter)
        val selections = listOf(
            BottomNavTab.ENCOUNTERS,
            BottomNavTab.COUNTER,
            BottomNavTab.ENCOUNTERS,
            BottomNavTab.ENCOUNTERS,
            BottomNavTab.COUNTER,
            BottomNavTab.COUNTER,
            BottomNavTab.ENCOUNTERS,
        )

        for (tab in selections) {
            backStack.selectTab(tab)
            val duplicates = backStack.groupingBy { it }.eachCount().filterValues { it > 1 }
            assertEquals(
                emptyMap<NavKey, Int>(),
                duplicates,
                "duplicate key(s) after selecting $tab: ${backStack.toList()}",
            )
        }
    }

    @Test
    fun `a stack built with a repeated key keeps that key once`() {
        val backStack = newStack(Counter, Encounters, Encounters)

        assertEquals(listOf<NavKey>(Counter, Encounters), backStack.toList())
    }

    @Test
    fun `deduplication keeps Counter as the root`() {
        val backStack = newStack(Counter, Encounters, Counter)

        assertEquals(Counter, backStack.first())
        assertEquals(listOf<NavKey>(Counter, Encounters), backStack.toList())
    }

    @Test
    fun `an empty stack falls back to the Counter root rather than rendering nothing`() {
        val backStack = newStack()

        assertEquals(listOf<NavKey>(Counter), backStack.toList())
    }

    @Test
    fun `push adds a key above the current tab`() {
        val backStack = newStack(Counter, Encounters)

        backStack.push(EncounterDetail("cat-1"))

        assertEquals(listOf<NavKey>(Counter, Encounters, EncounterDetail("cat-1")), backStack.toList())
    }

    @Test
    fun `pushing a key already on the stack leaves the stack unchanged`() {
        val backStack = newStack(Counter, Encounters)

        backStack.push(EncounterDetail("cat-1"))
        backStack.push(EncounterDetail("cat-1"))

        assertEquals(1, backStack.count { it == EncounterDetail("cat-1") })
        assertEquals(3, backStack.size)
    }

    @Test
    fun `a detail on top still reports the Encounters tab as selected`() {
        val backStack = newStack(Counter, Encounters, EncounterDetail("cat-1"))

        assertEquals(BottomNavTab.ENCOUNTERS, backStack.selectedTab)
    }

    @Test
    fun `selecting the Encounters tab from a detail returns to the list, not to a second copy of it`() {
        val backStack = newStack(Counter, Encounters, EncounterDetail("cat-1"))

        backStack.selectTab(BottomNavTab.ENCOUNTERS)

        assertEquals(listOf<NavKey>(Counter, Encounters), backStack.toList())
    }

    @Test
    fun `popOrNull from a detail lands on the list`() {
        val backStack = newStack(Counter, Encounters, EncounterDetail("cat-1"))

        assertTrue(backStack.popOrNull())

        assertEquals(listOf<NavKey>(Counter, Encounters), backStack.toList())
    }

    @Test
    fun `popOrNull removes the top entry and reports it popped when more than the root remains`() {
        val backStack = newStack(Counter, Encounters)

        assertTrue(backStack.popOrNull())

        assertEquals(listOf<NavKey>(Counter), backStack.toList())
    }

    @Test
    fun `popOrNull is a no-op on the root alone and reports nothing popped`() {
        val backStack = newStack(Counter)

        assertFalse(backStack.popOrNull())

        assertEquals(listOf<NavKey>(Counter), backStack.toList())
    }
}
