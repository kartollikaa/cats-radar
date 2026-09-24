package dev.catsradar.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dev.catsradar.domain.analytics.Analytics
import dev.catsradar.domain.analytics.AnalyticsEvent

class FirebaseAnalyticsReporter(context: Context) : Analytics {
    private val appContext = context.applicationContext
    private val firebase by lazy { FirebaseAnalytics.getInstance(appContext) }

    override fun log(event: AnalyticsEvent) {
        val encoded = event.encode()
        firebase.logEvent(encoded.name, encoded.params.toBundle())
    }
}

private fun Map<String, Any>.toBundle() = Bundle().apply {
    forEach { (key, value) ->
        when (value) {
            is String -> putString(key, value)
            is Long -> putLong(key, value)
            else -> error("Firebase takes String or Long parameters, not ${value::class.simpleName} ($key)")
        }
    }
}
