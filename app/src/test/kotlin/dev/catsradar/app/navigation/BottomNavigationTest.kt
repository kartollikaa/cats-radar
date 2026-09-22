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
