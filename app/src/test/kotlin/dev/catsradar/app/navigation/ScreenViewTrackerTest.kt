package dev.catsradar.app.navigation

import com.lemonappdev.konsist.api.Konsist
import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent
import dev.catsradar.domain.analytics.AnalyticsEvent.ScreenViewed
import dev.catsradar.domain.analytics.AnalyticsScreen
import org.junit.Test
import kotlin.test.assertEquals

private class RecordingAnalytics : Analytics {
    val logged = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        logged += event
    }
}

class ScreenViewTrackerTest {
    private val analytics = RecordingAnalytics()
    private val tracker = ScreenViewTracker(analytics)

    private val everyKey = mapOf(
        Counter to AnalyticsScreen.COUNTER,
        Encounters to AnalyticsScreen.ENCOUNTERS,
        EncounterDetail("cat-1") to AnalyticsScreen.ENCOUNTER_DETAIL,
        Statistics to AnalyticsScreen.STATISTICS,
        Regions(kind = RegionKind.COUNTRY, countryCode = "PT") to AnalyticsScreen.REGIONS,
        CatsMap to AnalyticsScreen.MAP,
        MapSpot(catIds = setOf("cat-1"), coats = emptySet()) to AnalyticsScreen.MAP_SPOT,
        Settings to AnalyticsScreen.SETTINGS,
    )

    @Test
    fun `every key is reported as its screen`() {
        everyKey.keys.forEach(tracker::onTop)

        assertEquals<List<AnalyticsEvent>>(everyKey.values.map(::ScreenViewed), analytics.logged)
    }

    @Test
    fun `every NavKey in the app is covered`() {
        val declared = Konsist.scopeFromPackage("dev.catsradar.app..")
            .classesAndObjects()
            .filter { it.hasParentWithName("NavKey") }
            .map { it.name }
            .toSet()

        assertEquals(declared, everyKey.keys.map { it::class.simpleName }.toSet())
    }

    @Test
    fun `the same screen on top again is not reported twice`() {
        tracker.onTop(Counter)
        tracker.onTop(EncounterDetail("cat-1"))
        tracker.onTop(EncounterDetail("cat-2"))
        tracker.onTop(EncounterDetail("cat-2"))

        assertEquals<List<AnalyticsEvent>>(
            listOf(ScreenViewed(AnalyticsScreen.COUNTER), ScreenViewed(AnalyticsScreen.ENCOUNTER_DETAIL)),
            analytics.logged,
        )
    }

    @Test
    fun `coming back to a screen reports it again`() {
        tracker.onTop(CatsMap)
        tracker.onTop(MapSpot(catIds = setOf("cat-1"), coats = emptySet()))
        tracker.onTop(CatsMap)

        assertEquals<List<AnalyticsEvent>>(
            listOf(AnalyticsScreen.MAP, AnalyticsScreen.MAP_SPOT, AnalyticsScreen.MAP).map(::ScreenViewed),
            analytics.logged,
        )
    }
}
