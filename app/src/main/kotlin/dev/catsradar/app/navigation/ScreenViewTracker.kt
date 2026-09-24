package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.analytics.AnalyticsScreen

// A process-wide single: a recreated activity puts the same key back on top and must not count again.
class ScreenViewTracker(private val analytics: Analytics) {
    private var topScreen: AnalyticsScreen? = null
    private var reported: AnalyticsScreen? = null
    private var resumed = false

    fun onTop(key: NavKey) {
        topScreen = key.analyticsScreen() ?: return
        report()
    }

    // Analytics drops a screen view logged before an activity resumes, so reporting waits for it.
    fun onAppResumed() {
        resumed = true
        report()
    }

    fun onAppPaused() {
        resumed = false
    }

    // Only a real stop starts a new visit; a dialog over the app pauses it without one.
    fun onAppStopped() {
        reported = null
    }

    // The process can outlive its activity; a new one starts from its own back stack, not the last one's top.
    fun onHostGone() {
        topScreen = null
    }

    private fun report() {
        val screen = topScreen ?: return
        if (!resumed || screen == reported) return
        reported = screen
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
