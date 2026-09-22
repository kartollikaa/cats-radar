package dev.catsradar.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import dev.catsradar.ui.navigation.BottomNavTab

/**
 * The app's back stack, restricted to the two shapes a Counter/Encounters bottom bar can produce:
 * `[Counter]` or `[Counter, tab]`. [selectTab] and [popOrNull] are the only way to change it - it
 * exposes `List`, never `MutableList`, so a caller cannot express a bare push. That closes the
 * hazard directly: navigation3-runtime 1.2.0-rc01 has no uniqueness guard of its own, and two
 * entries sharing a key would silently share one ViewModelStore.
 */
class BottomNavBackStack internal constructor(private val entries: NavBackStack<NavKey>) : List<NavKey> by entries {

    val selectedTab: BottomNavTab get() = if (last() == Counter) BottomNavTab.COUNTER else BottomNavTab.ENCOUNTERS

    fun selectTab(tab: BottomNavTab) {
        while (entries.size > 1) entries.removeAt(entries.lastIndex)
        val target = if (tab == BottomNavTab.COUNTER) Counter else Encounters
        if (entries.last() != target) entries.add(target)
    }

    /** Pops the top entry unless only the root remains; returns whether it popped. */
    fun popOrNull(): Boolean {
        if (entries.size <= 1) return false
        entries.removeAt(entries.lastIndex)
        return true
    }
}

@Composable
fun rememberBottomNavBackStack(): BottomNavBackStack {
    val entries = rememberNavBackStack(Counter)
    return remember(entries) { BottomNavBackStack(entries) }
}
