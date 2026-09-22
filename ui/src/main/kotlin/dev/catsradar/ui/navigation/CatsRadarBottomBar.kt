package dev.catsradar.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    NavigationBar(modifier = modifier) {
        BottomNavTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelect(tab) },
                icon = { Text(text = stringResource(tab.labelRes())) },
            )
        }
    }
}

@StringRes
private fun BottomNavTab.labelRes(): Int = when (this) {
    BottomNavTab.COUNTER -> R.string.tab_counter
    BottomNavTab.ENCOUNTERS -> R.string.tab_encounters
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
