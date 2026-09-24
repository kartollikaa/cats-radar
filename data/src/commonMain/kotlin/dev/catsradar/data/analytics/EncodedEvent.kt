package dev.catsradar.data.analytics

import dev.catsradar.domain.analytics.AnalyticsEvent

/** An event as Firebase takes it: every parameter value is a `String` or a `Long`. */
data class EncodedEvent(val name: String, val params: Map<String, Any>)

// Firebase's predefined screen view: its console reports read these two names.
private const val SCREEN_VIEW = "screen_view"
private const val SCREEN_NAME = "screen_name"

fun AnalyticsEvent.encode(): EncodedEvent = when (this) {
    is AnalyticsEvent.ScreenViewed -> EncodedEvent(SCREEN_VIEW, mapOf(SCREEN_NAME to screen.token))
}

private val Enum<*>.token: String get() = name.lowercase()
