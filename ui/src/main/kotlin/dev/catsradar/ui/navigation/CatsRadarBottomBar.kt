package dev.catsradar.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
fun CatsRadarBottomBar(
    selectedTab: BottomNavTab,
    modifier: Modifier = Modifier,
    onTabSelect: (BottomNavTab) -> Unit = {},
) {
    ShortNavigationBar(modifier = modifier) {
        BottomNavTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            ShortNavigationBarItem(
                selected = selected,
                onClick = { onTabSelect(tab) },
                // The label is always shown and names the tab, so the icon itself is decorative.
                icon = { Icon(painter = painterResource(tab.iconRes(selected)), contentDescription = null) },
                label = { Text(text = stringResource(tab.labelRes())) },
            )
        }
    }
}

@StringRes
private fun BottomNavTab.labelRes(): Int = when (this) {
    BottomNavTab.COUNTER -> R.string.tab_counter
    BottomNavTab.ENCOUNTERS -> R.string.tab_encounters
    BottomNavTab.MAP -> R.string.tab_map
    BottomNavTab.STATISTICS -> R.string.tab_statistics
    BottomNavTab.SETTINGS -> R.string.tab_settings
}

// Only the map and the gear have a filled form in Material Symbols; the other glyphs are already solid.
@DrawableRes
private fun BottomNavTab.iconRes(selected: Boolean): Int = when (this) {
    BottomNavTab.COUNTER -> R.drawable.ic_nav_pets
    BottomNavTab.ENCOUNTERS -> R.drawable.ic_nav_format_list_bulleted
    BottomNavTab.MAP -> if (selected) R.drawable.ic_nav_map_filled else R.drawable.ic_nav_map
    BottomNavTab.STATISTICS -> R.drawable.ic_nav_bar_chart
    BottomNavTab.SETTINGS -> if (selected) R.drawable.ic_nav_settings_filled else R.drawable.ic_nav_settings
}

@ThemePreviews
@Composable
private fun CatsRadarBottomBarCounterSelectedPreview() {
    CatsRadarTheme {
        Surface { CatsRadarBottomBar(selectedTab = BottomNavTab.COUNTER) }
    }
}

@ThemePreviews
@Composable
private fun CatsRadarBottomBarEncountersSelectedPreview() {
    CatsRadarTheme {
        Surface { CatsRadarBottomBar(selectedTab = BottomNavTab.ENCOUNTERS) }
    }
}
