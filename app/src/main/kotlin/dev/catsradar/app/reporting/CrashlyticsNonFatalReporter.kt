package dev.catsradar.app.reporting

import com.google.firebase.crashlytics.FirebaseCrashlytics

class CrashlyticsNonFatalReporter(crashlytics: Lazy<FirebaseCrashlytics>) : NonFatalReporter {
    private val crashlytics by crashlytics

    override fun record(error: Throwable) = crashlytics.recordException(error)
}
