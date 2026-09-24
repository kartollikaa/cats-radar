package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.analytics.AnalyticsScreen

// A process-wide single: a recreated activity puts the same key back on top and must not count again.
class ScreenViewTracker(private val analytics: Analytics) {
    private var current: AnalyticsScreen? = null

    fun onTop(key: NavKey) {
        val screen = key.analyticsScreen() ?: return
        if (screen == current) return
        current = screen
        analytics.log(AnalyticsEvent.ScreenViewed(screen))
    }
}

private fun NavKey.analyticsScreen(): AnalyticsScreen? = when (this) {
    Counter -> AnalyticsScreen.COUNTER
    Encounters -> AnalyticsScreen.ENCOUNTERS
    is EncounterDetail -> AnalyticsScreen.ENCOUNTER_DETAIL
    Statistics -> AnalyticsScreen.STATISTICS
    is Regions -> AnalyticsScreen.REGIONS
    CatsMap -> AnalyticsScreen.MAP
    is MapSpot -> AnalyticsScreen.MAP_SPOT
    Settings -> AnalyticsScreen.SETTINGS
    else -> null
}
