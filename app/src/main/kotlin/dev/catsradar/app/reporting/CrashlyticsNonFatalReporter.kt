package dev.catsradar.app.reporting

import com.google.firebase.crashlytics.FirebaseCrashlytics

class CrashlyticsNonFatalReporter(private val crashlytics: FirebaseCrashlytics) : NonFatalReporter {
    override fun record(error: Throwable) = crashlytics.recordException(error)
}
