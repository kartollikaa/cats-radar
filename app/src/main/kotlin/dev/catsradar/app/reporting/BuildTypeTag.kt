package dev.catsradar.app.reporting

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

private const val BUILD_TYPE_KEY = "build_type"

/** Tags every crash report and every analytics event from here on with the build they came from. */
fun tagReports(context: Context, buildType: String) {
    FirebaseCrashlytics.getInstance().setCustomKey(BUILD_TYPE_KEY, buildType)
    FirebaseAnalytics.getInstance(context).setUserProperty(BUILD_TYPE_KEY, buildType)
}
