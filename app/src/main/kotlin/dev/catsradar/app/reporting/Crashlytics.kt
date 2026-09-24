package dev.catsradar.app.reporting

import com.google.firebase.crashlytics.FirebaseCrashlytics

const val BUILD_TYPE_KEY = "build_type"

class CrashlyticsNonFatalReporter : NonFatalReporter {
    override fun record(error: Throwable) = FirebaseCrashlytics.getInstance().recordException(error)
}

fun tagCrashReports(buildType: String) = FirebaseCrashlytics.getInstance().setCustomKey(BUILD_TYPE_KEY, buildType)
