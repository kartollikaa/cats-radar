package dev.catsradar.data.analytics

import dev.catsradar.domain.analytics.AnalyticsEvent

data class EncodedEvent(
    val name: String,
    val texts: Map<String, String> = emptyMap(),
    val counts: Map<String, Long> = emptyMap(),
)

// Firebase's predefined screen view: its console reports read these two names.
private const val SCREEN_VIEW = "screen_view"
private const val SCREEN_NAME = "screen_name"

fun AnalyticsEvent.encode(): EncodedEvent = when (this) {
    is AnalyticsEvent.ScreenViewed -> EncodedEvent(SCREEN_VIEW, texts = mapOf(SCREEN_NAME to screen.token))
}

private val Enum<*>.token: String get() = name.lowercase()
