package dev.catsradar.domain.testing

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent

class RecordingAnalytics : Analytics {
    val logged = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        logged += event
    }
}
