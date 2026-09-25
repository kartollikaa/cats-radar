package dev.catsradar.presentation

import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent

object NoAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit
}
