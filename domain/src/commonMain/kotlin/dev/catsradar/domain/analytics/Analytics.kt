package dev.catsradar.domain.analytics

/** Returns at once: logging never waits on the network. */
interface Analytics {
    fun log(event: AnalyticsEvent)
}

/** Parameters are enums, booleans and counts only, so no event can carry a place, a photo, an id or a time. */
sealed interface AnalyticsEvent {
    data class ScreenViewed(val screen: AnalyticsScreen) : AnalyticsEvent
}

enum class AnalyticsScreen { COUNTER, ENCOUNTERS, ENCOUNTER_DETAIL, STATISTICS, REGIONS, MAP, MAP_SPOT, SETTINGS }
