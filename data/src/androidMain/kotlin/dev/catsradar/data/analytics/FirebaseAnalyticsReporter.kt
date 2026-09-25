package dev.catsradar.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent

class FirebaseAnalyticsReporter(private val firebase: FirebaseAnalytics) : Analytics {

    override fun log(event: AnalyticsEvent) {
        val encoded = event.encode()
        firebase.logEvent(encoded.name, encoded.toBundle())
    }
}

private fun EncodedEvent.toBundle() = Bundle().apply {
    texts.forEach { (key, value) -> putString(key, value) }
    counts.forEach { (key, value) -> putLong(key, value) }
}
