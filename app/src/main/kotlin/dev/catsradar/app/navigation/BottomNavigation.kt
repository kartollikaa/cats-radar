package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import dev.catsradar.ui.navigation.BottomNavTab

internal fun NavKey.toBottomNavTab(): BottomNavTab =
    if (this == Counter) BottomNavTab.COUNTER else BottomNavTab.ENCOUNTERS

/**
 * Rewrites [this] in place to [tab]'s root-anchored stack: `[Counter]` for the root tab,
 * `[Counter, tab]` otherwise. Always trims down to the shared root before appending, so the target
 * key is never already on the stack when it is added - navigation3-runtime 1.2.0-rc01 has no
 * uniqueness guard and would silently let two entries with the same key share one ViewModelStore.
 */
internal fun MutableList<NavKey>.selectBottomNavTab(tab: BottomNavTab) {
    while (size > 1) removeAt(lastIndex)
    val target = if (tab == BottomNavTab.COUNTER) Counter else Encounters
    if (last() != target) add(target)
}
