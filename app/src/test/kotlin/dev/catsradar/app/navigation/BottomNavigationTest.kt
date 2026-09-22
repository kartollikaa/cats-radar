package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import dev.catsradar.ui.navigation.BottomNavTab
import org.junit.Test
import kotlin.test.assertEquals

class BottomNavigationTest {

    @Test
    fun `the Counter key maps to the Counter tab and any other key maps to Encounters`() {
        assertEquals(BottomNavTab.COUNTER, Counter.toBottomNavTab())
        assertEquals(BottomNavTab.ENCOUNTERS, Encounters.toBottomNavTab())
    }

    @Test
    fun `selecting Encounters from the Counter root pushes it once`() {
        val backStack = mutableListOf<NavKey>(Counter)

        backStack.selectBottomNavTab(BottomNavTab.ENCOUNTERS)

        assertEquals(listOf(Counter, Encounters), backStack)
    }

    @Test
    fun `selecting Counter while on Encounters returns to the single-entry root stack`() {
        val backStack = mutableListOf<NavKey>(Counter, Encounters)

        backStack.selectBottomNavTab(BottomNavTab.COUNTER)

        assertEquals(listOf<NavKey>(Counter), backStack)
    }

    @Test
    fun `selecting the already-active tab again never leaves a duplicate key on the stack`() {
        val backStack = mutableListOf<NavKey>(Counter)

        backStack.selectBottomNavTab(BottomNavTab.ENCOUNTERS)
        backStack.selectBottomNavTab(BottomNavTab.ENCOUNTERS)

        assertEquals(listOf(Counter, Encounters), backStack)
        assertEquals(1, backStack.count { it == Encounters })
    }

    @Test
    fun `every tab selection in a mixed sequence leaves each key on the stack at most once`() {
        val backStack = mutableListOf<NavKey>(Counter)
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
            backStack.selectBottomNavTab(tab)
            val duplicates = backStack.groupingBy { it }.eachCount().filterValues { it > 1 }
            assertEquals(emptyMap<NavKey, Int>(), duplicates, "duplicate key(s) after selecting $tab: $backStack")
        }
    }
}
