package dev.catsradar.ui.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews

@Composable
fun CatsRadarBottomBar(
    selectedTab: BottomNavTab,
    modifier: Modifier = Modifier,
    onTabSelect: (BottomNavTab) -> Unit = {},
) {
    NavigationBar(modifier = modifier) {
        BottomNavTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelect(tab) },
                icon = { Text(text = tab.label()) },
            )
        }
    }
}

private fun BottomNavTab.label(): String = when (this) {
    BottomNavTab.COUNTER -> "Counter"
    BottomNavTab.ENCOUNTERS -> "Encounters"
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
