package dev.catsradar.app.reporting

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

private const val BUILD_TYPE_KEY = "build_type"

/** Tags every crash report and every analytics event from here on with the build they came from. */
fun tagReports(crashlytics: FirebaseCrashlytics, analytics: FirebaseAnalytics, buildType: String) {
    crashlytics.setCustomKey(BUILD_TYPE_KEY, buildType)
    analytics.setUserProperty(BUILD_TYPE_KEY, buildType)
}
