package dev.catsradar.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import dev.catsradar.ui.navigation.BottomNavTab

/**
 * The app's back stack. No key can appear on it twice: navigation3-runtime 1.2.0-rc01 has no
 * uniqueness guard of its own, `NavEntry.contentKey` defaults to the key, and two entries sharing a
 * contentKey silently share one ViewModelStore. The invariant is enforced on construction, so no
 * caller - including saved-state restoration - can hold an instance that violates it.
 */
class BottomNavBackStack internal constructor(private val entries: NavBackStack<NavKey>) : List<NavKey> by entries {

    init {
        dropDuplicateKeys()
    }

    // Reading the tab back out of the stack rather than storing it: a detail pushed above a tab
    // must still report the tab it belongs to.
    val selectedTab: BottomNavTab
        get() = BottomNavTab.entries.firstOrNull { it != BottomNavTab.COUNTER && it.key() in entries }
            ?: BottomNavTab.COUNTER

    fun selectTab(tab: BottomNavTab) {
        while (entries.size > 1) entries.removeAt(entries.lastIndex)
        val target = tab.key()
        if (entries.last() != target) entries.add(target)
    }

    /** Pushes [key] unless it is already on the stack, in which case nothing changes. */
    fun push(key: NavKey) {
        if (key !in entries) entries.add(key)
    }

    /** Pops the top entry unless only the root remains; returns whether it popped. */
    fun popOrNull(): Boolean {
        if (entries.size <= 1) return false
        entries.removeAt(entries.lastIndex)
        return true
    }

    // Exhaustive on purpose: a new tab will not compile until it has a destination here.
    private fun BottomNavTab.key(): NavKey = when (this) {
        BottomNavTab.COUNTER -> Counter
        BottomNavTab.ENCOUNTERS -> Encounters
        BottomNavTab.STATISTICS -> Statistics
        BottomNavTab.SETTINGS -> Settings
    }

    private fun dropDuplicateKeys() {
        if (entries.isEmpty()) {
            entries.add(Counter)
            return
        }
        // Keeping the first occurrence rather than the last is what preserves Counter as the root.
        val deduplicated = entries.distinct()
        if (deduplicated.size == entries.size) return
        entries.clear()
        entries.addAll(deduplicated)
    }
}

@Composable
fun rememberBottomNavBackStack(): BottomNavBackStack {
    val entries = rememberNavBackStack(Counter)
    return remember(entries) { BottomNavBackStack(entries) }
}
