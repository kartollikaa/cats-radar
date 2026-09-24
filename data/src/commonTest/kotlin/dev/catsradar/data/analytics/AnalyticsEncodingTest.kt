package dev.catsradar.data.analytics

import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.analytics.AnalyticsEvent.ScreenViewed
import dev.catsradar.domain.analytics.AnalyticsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val FirebaseName = Regex("[a-zA-Z][a-zA-Z0-9_]{0,39}")
private val ReservedPrefixes = listOf("firebase_", "google_", "ga_")
private const val MAX_TEXT_VALUE = 100

// A new event type stops compiling here until it is listed in everyEvent below.
private fun sampled(event: AnalyticsEvent): Unit = when (event) {
    is ScreenViewed -> Unit
}

private val everyEvent: List<AnalyticsEvent> = AnalyticsScreen.entries.map(::ScreenViewed)

class AnalyticsEncodingTest {

    @Test
    fun aScreenViewIsFirebasesScreenViewNamedByItsScreen() {
        val expected = mapOf(
            AnalyticsScreen.COUNTER to "counter",
            AnalyticsScreen.ENCOUNTERS to "encounters",
            AnalyticsScreen.ENCOUNTER_DETAIL to "encounter_detail",
            AnalyticsScreen.STATISTICS to "statistics",
            AnalyticsScreen.REGIONS to "regions",
            AnalyticsScreen.MAP to "map",
            AnalyticsScreen.MAP_SPOT to "map_spot",
            AnalyticsScreen.SETTINGS to "settings",
        )

        assertEquals(AnalyticsScreen.entries.toSet(), expected.keys)
        expected.forEach { (screen, token) ->
            assertEquals(EncodedEvent("screen_view", mapOf("screen_name" to token)), ScreenViewed(screen).encode())
        }
    }

    @Test
    fun everyNameAndParameterFitsFirebasesLimits() {
        everyEvent.forEach { event ->
            sampled(event)
            val encoded = event.encode()
            (listOf(encoded.name) + encoded.params.keys).forEach { name ->
                assertTrue(FirebaseName.matches(name), "$name is not a valid Firebase name")
                assertTrue(ReservedPrefixes.none(name::startsWith), "$name uses a reserved prefix")
            }
            encoded.params.values.forEach { value ->
                assertTrue(value is Long || (value is String && value.length <= MAX_TEXT_VALUE), "$value in $event")
            }
        }
    }
}
